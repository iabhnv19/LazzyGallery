use jni::JNIEnv;
use jni::objects::{JClass, JIntArray, JLongArray};
use jni::sys::{jbyteArray, jintArray, jlongArray, jstring};
use rayon::prelude::*;
use std::fs::File;
use std::os::unix::io::FromRawFd;
use std::io::{Read, Cursor, BufReader};
use image::GenericImageView;
use image::io::Reader as ImageReader;
use imageproc::contrast::contrast;

fn read_fd_bytes(fd: i32, max_bytes: Option<u64>) -> Option<Vec<u8>> {
    if fd < 0 {
        return None;
    }
    let cloned_fd = unsafe { libc::dup(fd) };
    if cloned_fd < 0 {
        return None;
    }
    let file = unsafe { File::from_raw_fd(cloned_fd) };
    let mut buffer = Vec::new();
    let res = match max_bytes {
        Some(limit) => file.take(limit).read_to_end(&mut buffer),
        None => BufReader::new(file).read_to_end(&mut buffer),
    };
    if res.is_err() || buffer.is_empty() {
        return None;
    }
    Some(buffer)
}

fn decode_scaled_image(buffer: &[u8], target_w: u16, target_h: u16) -> Option<image::DynamicImage> {
    // Fast path for JPEGs: use DCT-scaled decoding to decode at 1/2, 1/4, or 1/8 resolution
    // directly during IDCT instead of decoding the entire multi-megapixel image into RAM.
    if buffer.len() >= 2 && buffer[0] == 0xFF && buffer[1] == 0xD8 {
        let mut decoder = jpeg_decoder::Decoder::new(Cursor::new(buffer));
        if decoder.read_info().is_ok() {
            let _ = decoder.scale(target_w, target_h);
            if let Ok(pixels) = decoder.decode() {
                if let Some(info) = decoder.info() {
                    let w = info.width as u32;
                    let h = info.height as u32;
                    match info.pixel_format {
                        jpeg_decoder::PixelFormat::RGB24 => {
                            if let Some(img_buf) = image::RgbImage::from_raw(w, h, pixels) {
                                return Some(image::DynamicImage::ImageRgb8(img_buf));
                            }
                        }
                        jpeg_decoder::PixelFormat::L8 => {
                            if let Some(img_buf) = image::GrayImage::from_raw(w, h, pixels) {
                                return Some(image::DynamicImage::ImageLuma8(img_buf));
                            }
                        }
                        _ => {}
                    }
                }
            }
        }
    }

    let reader = ImageReader::new(Cursor::new(buffer)).with_guessed_format().ok()?;
    reader.decode().ok()
}

fn compute_dhash_from_fd(fd: i32) -> i64 {
    let buffer = match read_fd_bytes(fd, None) {
        Some(b) => b,
        None => return 0,
    };

    let img = match decode_scaled_image(&buffer, 64, 64) {
        Some(i) => i,
        None => return 0,
    };

    // Resize first, then convert only the 9x8 (72 pixels) to luma instead of
    // allocating a full-resolution grayscale buffer.
    let luma = img
        .resize_exact(9, 8, image::imageops::FilterType::Nearest)
        .to_luma8();

    let mut hash: u64 = 0;
    for y in 0..8 {
        for x in 0..8 {
            let left = luma.get_pixel(x, y)[0];
            let right = luma.get_pixel(x + 1, y)[0];
            hash = (hash << 1) | if left > right { 1 } else { 0 };
        }
    }

    hash as i64
}

/// Decodes an image from a raw file descriptor (fd), resizes it to target dimensions,
/// and returns raw RGBA8888 bytes prefixed by 8-byte little-endian (width, height).
/// Avoids double JPEG encode/decode overhead across JNI.
#[no_mangle]
pub extern "system" fn Java_com_lazzy_gallery_data_RustBridge_decodeThumbnailRgbaNative(
    mut env: JNIEnv,
    _class: JClass,
    fd: i32,
    target_width: i32,
    target_height: i32,
) -> jbyteArray {
    let buffer = match read_fd_bytes(fd, None) {
        Some(b) => b,
        None => return std::ptr::null_mut(),
    };

    let tw = target_width.max(64).min(u16::MAX as i32) as u32;
    let th = target_height.max(64).min(u16::MAX as i32) as u32;

    let img = match decode_scaled_image(&buffer, tw as u16, th as u16) {
        Some(i) => i,
        None => return std::ptr::null_mut(),
    };

    let (w, h) = img.dimensions();

    let resized = if w > tw || h > th {
        img.resize(tw, th, image::imageops::FilterType::Triangle)
    } else {
        img
    };

    let rgba = resized.to_rgba8();
    let (rw, rh) = rgba.dimensions();
    let raw_pixels = rgba.into_raw();

    let mut out = Vec::with_capacity(8 + raw_pixels.len());
    out.extend_from_slice(&rw.to_le_bytes());
    out.extend_from_slice(&rh.to_le_bytes());
    out.extend_from_slice(&raw_pixels);

    match env.byte_array_from_slice(&out) {
        Ok(arr) => arr,
        Err(_) => std::ptr::null_mut(),
    }
}

/// Decodes an image from a raw file descriptor (fd), resizes it to target dimensions,
/// and returns compressed JPEG thumbnail bytes.
#[no_mangle]
pub extern "system" fn Java_com_lazzy_gallery_data_RustBridge_decodeThumbnailNative(
    mut env: JNIEnv,
    _class: JClass,
    fd: i32,
    target_width: i32,
    target_height: i32,
) -> jbyteArray {
    let buffer = match read_fd_bytes(fd, None) {
        Some(b) => b,
        None => return std::ptr::null_mut(),
    };

    let tw = target_width.max(64).min(u16::MAX as i32) as u32;
    let th = target_height.max(64).min(u16::MAX as i32) as u32;

    let img = match decode_scaled_image(&buffer, tw as u16, th as u16) {
        Some(i) => i,
        None => return std::ptr::null_mut(),
    };

    let (w, h) = img.dimensions();

    let resized = if w > tw || h > th {
        img.resize(tw, th, image::imageops::FilterType::Triangle)
    } else {
        img
    };

    let mut jpg_bytes = Vec::new();
    let mut cursor = Cursor::new(&mut jpg_bytes);
    if resized.write_to(&mut cursor, image::ImageFormat::Jpeg).is_err() {
        return std::ptr::null_mut();
    }

    match env.byte_array_from_slice(&jpg_bytes) {
        Ok(arr) => arr,
        Err(_) => std::ptr::null_mut(),
    }
}

/// Preprocesses an image for OCR: converts to grayscale and enhances contrast.
#[no_mangle]
pub extern "system" fn Java_com_lazzy_gallery_data_RustBridge_preprocessOcrNative(
    mut env: JNIEnv,
    _class: JClass,
    fd: i32,
) -> jbyteArray {
    if fd < 0 {
        return std::ptr::null_mut();
    }

    let cloned_fd = unsafe { libc::dup(fd) };
    if cloned_fd < 0 {
        return std::ptr::null_mut();
    }

    let mut file = unsafe { File::from_raw_fd(cloned_fd) };
    let mut buffer = Vec::new();
    if file.read_to_end(&mut buffer).is_err() || buffer.is_empty() {
        return std::ptr::null_mut();
    }

    let reader = match ImageReader::new(Cursor::new(buffer)).with_guessed_format() {
        Ok(r) => r,
        Err(_) => return std::ptr::null_mut(),
    };

    let img = match reader.decode() {
        Ok(i) => i,
        Err(_) => return std::ptr::null_mut(),
    };

    let luma = img.to_luma8();
    let enhanced = contrast(&luma, 30);

    let mut jpg_bytes = Vec::new();
    let mut cursor = Cursor::new(&mut jpg_bytes);
    if enhanced.write_to(&mut cursor, image::ImageFormat::Jpeg).is_err() {
        return std::ptr::null_mut();
    }

    match env.byte_array_from_slice(&jpg_bytes) {
        Ok(arr) => arr,
        Err(_) => std::ptr::null_mut(),
    }
}

#[derive(serde::Serialize)]
struct ExifInfo {
    camera_make: Option<String>,
    camera_model: Option<String>,
    datetime: Option<String>,
    width: Option<u32>,
    height: Option<u32>,
}

/// Extracts EXIF metadata by reading only the file header (up to 256 KB) and returns a JSON string.
#[no_mangle]
pub extern "system" fn Java_com_lazzy_gallery_data_RustBridge_extractExifNative(
    mut env: JNIEnv,
    _class: JClass,
    fd: i32,
) -> jstring {
    let buffer = match read_fd_bytes(fd, Some(256 * 1024)) {
        Some(b) => b,
        None => return std::ptr::null_mut(),
    };

    let exif_data = match rexif::parse_buffer(&buffer) {
        Ok(d) => d,
        Err(_) => return std::ptr::null_mut(),
    };

    let mut make = None;
    let mut model = None;
    let mut datetime = None;

    for entry in exif_data.entries {
        match entry.tag {
            rexif::Tag::Make => make = Some(entry.value_more_readable),
            rexif::Tag::Model => model = Some(entry.value_more_readable),
            rexif::Tag::DateTime | rexif::Tag::DateTimeOriginal => datetime = Some(entry.value_more_readable),
            _ => {}
        }
    }

    let reader = ImageReader::new(Cursor::new(&buffer)).with_guessed_format();
    let (w, h) = if let Ok(r) = reader {
        if let Ok(dims) = r.into_dimensions() {
            (Some(dims.0), Some(dims.1))
        } else {
            (None, None)
        }
    } else {
        (None, None)
    };

    let info = ExifInfo {
        camera_make: make,
        camera_model: model,
        datetime,
        width: w,
        height: h,
    };

    let json_str = match serde_json::to_string(&info) {
        Ok(s) => s,
        Err(_) => "{}".to_string(),
    };

    match env.new_string(json_str) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Computes a 64-bit perceptual difference hash (dHash) for duplicate detection.
#[no_mangle]
pub extern "system" fn Java_com_lazzy_gallery_data_RustBridge_computePHashNative(
    _env: JNIEnv,
    _class: JClass,
    fd: i32,
) -> i64 {
    compute_dhash_from_fd(fd)
}

/// Computes 64-bit dHashes in parallel across CPU cores using Rayon for a batch of file descriptors.
#[no_mangle]
pub extern "system" fn Java_com_lazzy_gallery_data_RustBridge_computePHashesBatchNative(
    env: JNIEnv,
    _class: JClass,
    fds: JIntArray,
) -> jlongArray {
    let len = match env.get_array_length(&fds) {
        Ok(l) => l as usize,
        Err(_) => return std::ptr::null_mut(),
    };
    if len == 0 {
        return match env.new_long_array(0) {
            Ok(arr) => arr.into_raw(),
            Err(_) => std::ptr::null_mut(),
        };
    }

    let mut fd_buf = vec![0i32; len];
    if env.get_int_array_region(&fds, 0, &mut fd_buf).is_err() {
        return std::ptr::null_mut();
    }

    let hashes: Vec<i64> = fd_buf
        .par_iter()
        .map(|&fd| compute_dhash_from_fd(fd))
        .collect();

    let out = match env.new_long_array(len as i32) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };
    if env.set_long_array_region(&out, 0, &hashes).is_err() {
        return std::ptr::null_mut();
    }
    out.into_raw()
}

/// Groups duplicate items in Rust by Hamming distance <= max_distance.
/// Returns a flat array of group indices (-1 separates groups).
#[no_mangle]
pub extern "system" fn Java_com_lazzy_gallery_data_RustBridge_groupDuplicateHashesNative(
    env: JNIEnv,
    _class: JClass,
    hashes: JLongArray,
    max_distance: i32,
) -> jintArray {
    let len = match env.get_array_length(&hashes) {
        Ok(l) => l as usize,
        Err(_) => return std::ptr::null_mut(),
    };
    if len < 2 {
        return match env.new_int_array(0) {
            Ok(arr) => arr.into_raw(),
            Err(_) => std::ptr::null_mut(),
        };
    }

    let mut hash_buf = vec![0i64; len];
    if env.get_long_array_region(&hashes, 0, &mut hash_buf).is_err() {
        return std::ptr::null_mut();
    }

    let threshold = max_distance.max(0) as u32;
    let mut visited = vec![false; len];
    let mut result: Vec<i32> = Vec::new();

    for i in 0..len {
        if visited[i] {
            continue;
        }
        let h_a = hash_buf[i] as u64;
        let mut group = vec![i as i32];
        for j in (i + 1)..len {
            if visited[j] {
                continue;
            }
            if (h_a ^ (hash_buf[j] as u64)).count_ones() <= threshold {
                group.push(j as i32);
                visited[j] = true;
            }
        }
        if group.len() > 1 {
            visited[i] = true;
            result.extend_from_slice(&group);
            result.push(-1);
        }
    }

    let out = match env.new_int_array(result.len() as i32) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };
    if !result.is_empty() && env.set_int_array_region(&out, 0, &result).is_err() {
        return std::ptr::null_mut();
    }
    out.into_raw()
}
