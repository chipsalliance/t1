use std::sync::Mutex;

pub struct DpiTarget<T> {
  target: Mutex<Option<T>>,
}

impl<T> DpiTarget<T> {
  pub const fn new() -> Self {
    Self { target: Mutex::new(None) }
  }

  #[track_caller]
  pub fn init(&self, init_fn: impl FnOnce() -> T) {
    let mut target = self.target.lock().unwrap();
    if target.is_some() {
      panic!("DpiTarget is already initialized");
    }
    *target = Some(init_fn());
  }

  #[track_caller]
  pub fn with<R>(&self, f: impl FnOnce(&mut T) -> R) -> R {
    let mut target = self.target.lock().unwrap();
    let target = target.as_mut().expect("DpiTarget is not initialized");
    f(target)
  }

  #[track_caller]
  pub fn with_optional<R>(&self, f: impl FnOnce(Option<&mut T>) -> R) -> R {
    let mut target = self.target.lock().unwrap();
    f(target.as_mut())
  }

  #[track_caller]
  pub fn dispose(&self) {
    let mut target = self.target.lock().unwrap();
    if target.is_none() {
      panic!("DpiTarget is not initialized");
    }
    *target = None;
  }
}
