//! Linux/Android descriptor-relative read-only opening. A path rename cannot
//! substitute a symbolic-link target after the URI has been validated.
use std::ffi::CString;
use std::fs::File;
use std::io;
use std::os::fd::{AsRawFd, FromRawFd};
use std::os::raw::{c_char, c_int};

// Linux UAPI: ARMv7 overrides the asm-generic directory/no-follow bits.
// Sources: arch/arm/include/uapi/asm/fcntl.h and include/uapi/asm-generic/fcntl.h.
#[cfg(target_arch="arm")]
const O_DIRECTORY: c_int = 1 << 14;
#[cfg(not(target_arch="arm"))]
const O_DIRECTORY: c_int = 1 << 16;
#[cfg(target_arch="arm")]
const O_NOFOLLOW: c_int = 1 << 15;
#[cfg(not(target_arch="arm"))]
const O_NOFOLLOW: c_int = 1 << 17;
#[cfg(target_arch="arm")]
const O_LARGEFILE: c_int = 1 << 17;
#[cfg(not(target_arch="arm"))]
const O_LARGEFILE: c_int = 1 << 15;
const O_CLOEXEC: c_int = 0x80000;
const O_NONBLOCK: c_int = 0x800;
extern "C" {
    fn open(path: *const c_char, flags: c_int, ...) -> c_int;
    fn openat(dirfd: c_int, path: *const c_char, flags: c_int, ...) -> c_int;
}
fn invalid() -> io::Error { io::Error::new(io::ErrorKind::PermissionDenied, "Invalid shared file") }
fn owned(fd: c_int) -> io::Result<File> {
    if fd < 0 { return Err(io::Error::last_os_error()); }
    // SAFETY: every successful open/openat returns a newly owned descriptor.
    Ok(unsafe { File::from_raw_fd(fd) })
}
pub(crate) fn open_shared(root: &str, relative: &str) -> io::Result<File> {
    if root.len() > 4096 || !root.starts_with('/') || relative.is_empty() || relative.len() > 4096 {
        return Err(invalid());
    }
    let parts: Vec<&str> = relative.split('/').collect();
    if parts.len() > 64 || parts.iter().any(|p|p.is_empty() || *p=="." || *p==".." || p.len()>255)
        || (parts.first()==Some(&"Android") && matches!(parts.get(1),Some(&"data"|&"obb"))) {
        return Err(invalid());
    }
    let root = CString::new(root).map_err(|_|invalid())?;
    // SAFETY: a live NUL-terminated CString and read-only flags; no creation mode.
    let mut directory = owned(unsafe { open(root.as_ptr(),O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC|O_LARGEFILE) })?;
    for (index, part) in parts.iter().enumerate() {
        let name = CString::new(*part).map_err(|_|invalid())?;
        let last = index==parts.len()-1;
        let flags=O_NOFOLLOW|O_CLOEXEC|O_NONBLOCK|O_LARGEFILE|if last {0}else{O_DIRECTORY};
        // SAFETY: directory remains owned/live for this call; name is a valid
        // CString. O_NOFOLLOW applies at every component, not just the leaf.
        let next=owned(unsafe { openat(directory.as_raw_fd(),name.as_ptr(),flags) })?;
        if last {
            if !next.metadata()?.is_file() { return Err(invalid()); }
            return Ok(next);
        }
        directory=next;
    }
    Err(invalid())
}
#[cfg(test)] mod tests {
    use super::*;
    use std::io::Read;
    use std::os::unix::fs::symlink;
    struct Fixture(std::path::PathBuf);
    impl Fixture {
        fn new()->Self {let p=std::env::temp_dir().join(format!("grove-open-{}-{}",std::process::id(),std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));std::fs::create_dir(&p).unwrap();Self(p)}
        fn root(&self)->&str {self.0.to_str().unwrap()}
    }
    impl Drop for Fixture {fn drop(&mut self){let _=std::fs::remove_dir_all(&self.0);}}
    #[test] fn refuses_escape_links_protected_paths_and_nonfiles(){
        let f=Fixture::new();std::fs::create_dir(f.0.join("inside")).unwrap();std::fs::write(f.0.join("inside/good"),"good").unwrap();
        symlink("inside/good",f.0.join("leaf")).unwrap();symlink("inside",f.0.join("link")).unwrap();
        for path in ["../outside","leaf","link/good","inside","Android/data/a","Android/obb/a","/inside/good","inside/../good"] {assert!(open_shared(f.root(),path).is_err(),"{path}");}
        let mut text=String::new();open_shared(f.root(),"inside/good").unwrap().read_to_string(&mut text).unwrap();assert_eq!(text,"good");
    }
    #[test] fn swapping_ancestor_for_link_never_opens_outside_file(){
        let root=Fixture::new();let outside=Fixture::new();std::fs::create_dir(root.0.join("lane")).unwrap();
        std::fs::write(root.0.join("lane/file"),"inside").unwrap();std::fs::write(outside.0.join("file"),"outside").unwrap();
        let path=root.0.clone();let destination=outside.0.clone();
        let mutator=std::thread::spawn(move||{for _ in 0..500 {
            std::fs::rename(path.join("lane"),path.join("parked")).unwrap();symlink(&destination,path.join("lane")).unwrap();
            std::fs::remove_file(path.join("lane")).unwrap();std::fs::rename(path.join("parked"),path.join("lane")).unwrap();
        }});
        for _ in 0..2000 {if let Ok(mut file)=open_shared(root.root(),"lane/file"){let mut text=String::new();file.read_to_string(&mut text).unwrap();assert_eq!(text,"inside");}}
        mutator.join().unwrap();
    }
}
