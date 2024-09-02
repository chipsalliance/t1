pub mod dpi_target;
pub mod dump;
pub mod plusarg;
pub mod util;

pub use dpi_target::DpiTarget;

use tracing_subscriber::{EnvFilter, FmtSubscriber};

pub fn setup_logger() {
  let global_logger = FmtSubscriber::builder()
    .with_env_filter(EnvFilter::from_default_env()) // default level: error
    .without_time()
    .with_target(false)
    .with_ansi(true)
    .compact()
    .finish();
  tracing::subscriber::set_global_default(global_logger)
    .expect("internal error: fail to setup log subscriber");
}
