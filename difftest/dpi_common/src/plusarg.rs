pub struct PlusArgMatcher {
  plusargs: Vec<String>,
}

impl PlusArgMatcher {
  pub fn from_args() -> Self {
    let plusargs = std::env::args().filter(|arg| arg.starts_with('+')).collect();

    Self { plusargs }
  }

  pub fn try_match(&self, arg_name: &str) -> Option<&str> {
    let prefix = &format!("+{arg_name}=");

    for plusarg in &self.plusargs {
      if plusarg.starts_with(prefix) {
        return Some(&plusarg[prefix.len()..]);
      }
    }
    None
  }

  pub fn match_(&self, arg_name: &str) -> &str {
    self.try_match(arg_name).unwrap_or_else(|| {
      tracing::error!("required plusarg '+{arg_name}=' not found");
      panic!("failed to match '+{arg_name}='");
    })
  }
}
