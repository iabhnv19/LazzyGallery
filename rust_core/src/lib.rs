use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::{jbyteArray, jstring};
use std::fs::File;
use std::os::unix::io::FromRawFd;
use std::io::{Read, Cursor};
use image::GenericImageView;
use image::io::Reader as ImageReader;
use imageproc::contrast::contrast;

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

    let (w, h) = img.dimensions();
    let tw = target_width.max(64) as u32;
    let th = target_height.max(64) as u32;

    let resized = if w > tw || h > th {
        img.thumbnail(tw, th)
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

    let gray = img.grayscale();
    let enhanced = contrast(&gray.to_luma8(), 30);

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

/// Extracts EXIF metadata and returns a JSON string.
#[no_mangle]
pub extern "system" fn Java_com_lazzy_gallery_data_RustBridge_extractExifNative(
    mut env: JNIEnv,
    _class: JClass,
    fd: i32,
) -> jstring {
    if fd < 0 {
        return std::ptr::null_mut();
    }

    let cloned_fd = unsafe { libc::dup(fd) };
    if cloned_fd < 0 {
        return std::ptr::null_mut();
    }

    let mut file = unsafe { File::from_raw_fd(cloned_fd) };

    let mut buffer = Vec::new();
    if file.read_to_end(&mut buffer).is_err() {
        return std::ptr::null_mut();
    }

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
    if fd < 0 {
        return 0;
    }

    let cloned_fd = unsafe { libc::dup(fd) };
    if cloned_fd < 0 {
        return 0;
    }

    let mut file = unsafe { File::from_raw_fd(cloned_fd) };
    let mut buffer = Vec::new();
    if file.read_to_end(&mut buffer).is_err() || buffer.is_empty() {
        return 0;
    }

    let reader = match ImageReader::new(Cursor::new(buffer)).with_guessed_format() {
        Ok(r) => r,
        Err(_) => return 0,
    };

    let img = match reader.decode() {
        Ok(i) => i,
        Err(_) => return 0,
    };

    let small = img.grayscale().resize_exact(9, 8, image::imageops::FilterType::Triangle);
    let luma = small.to_luma8();

    let mut hash: u64 = 0;
    for y in 0..8 {
        for x in 0..8 {
            let left = luma.get_pixel(x, y)[0];
            let right = luma.get_pixel(x + 1, y)[0];
            if left > right {
                hash |= 1;
            }
            hash <<= 1;
        }
    }

    hash as i64
}
