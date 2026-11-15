use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::{jbyteArray, jstring};
use std::fs::File;
use std::os::unix::io::FromRawFd;
use image::GenericImageView;
use image::io::Reader as ImageReader;
use imageproc::contrast::contrast;

/// Decodes an image from a raw file descriptor (fd), resizes it to target dimensions,
/// and returns compressed JPEG thumbnail bytes.
#[no_mangle]
pub extern "system" fn Java_com_lazzy_gallery_data_RustBridge_nativeDecodeThumbnail(
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

    let file = unsafe { File::from_raw_fd(cloned_fd) };

    let reader = match ImageReader::new(std::io::BufReader::new(file)).with_guessed_format() {
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
    let mut cursor = std::io::Cursor::new(&mut jpg_bytes);
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
pub extern "system" fn Java_com_lazzy_gallery_data_RustBridge_nativePreprocessOcr(
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

    let file = unsafe { File::from_raw_fd(cloned_fd) };
    let reader = match ImageReader::new(std::io::BufReader::new(file)).with_guessed_format() {
        Ok(r) => r,
        Err(_) => return std::ptr::null_mut(),
    };

    let img = match reader.decode() {
        Ok(i) => i,
        Err(_) => return std::ptr::null_mut(),
    };

    // Convert to grayscale and increase contrast for crisp text recognition
    let gray = img.grayscale();
    let enhanced = contrast(&gray.to_luma8(), 30);

    let mut jpg_bytes = Vec::new();
    let mut cursor = std::io::Cursor::new(&mut jpg_bytes);
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
pub extern "system" fn Java_com_lazzy_gallery_data_RustBridge_nativeExtractExif(
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
    use std::io::Read;
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

    // Try reading dimensions via image reader as well
    let reader = ImageReader::new(std::io::Cursor::new(&buffer)).with_guessed_format();
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
