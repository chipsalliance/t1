package v

import os._

package object tests {
  def resource(file: String): Path = Path(java.nio.file.Paths.get(getClass().getClassLoader().getResource(file).toURI))
}
