#import "@preview/cetz:0.3.4": canvas, draw

// Convert integer to fixed-length bit array string representation
#let to_binary(value, width: 8) = {
  let mask = int(calc.pow(2, width)) - 1 
  let masked-value = value.bit-and(mask)

  let s = str(masked-value, base: 2)

  if s.len() < width {
    range(width - s.len()).map(_ => "0").join() + s
  } else {
    s
  }
}

// A deterministic str to color calculation.
#let _string_to_color(s) = {
  let colorset = color.map.magma
  let id = 5381
  // Iterate over the byte representation of the string
  for b in bytes(s) {
    // h = h * 33 + b
    // We use calc.rem (remainder) to keep the integer within standard bounds
    // and prevent potential overflow errors in some contexts,
    // though Typst handles large integers well.
    id = calc.rem(id * 33 + b, 2147483647)
  }

  colorset.at(calc.rem(id, colorset.len())).lighten(70%)
}

/// Draws a sliced bit vector with manual index support.
///
/// - fields: Array of field dictionaries.
///    - bits: (int) Width of field.
///    - content: (string) Text to display.
///    - type: (string, optional) "var" (default) or "static".
///    - msb: (string/int, optional) Manual label for the top-left index.
///    - lsb: (string/int, optional) Manual label for the top-right index.
/// - scale-factor: (float) Resize the entire diagram.
#let draw_bitvector(fields, scale-factor: 1, fg: black) = {
  scale(scale-factor * 100%, reflow: true)[
    #canvas({
      import draw: *

      // --- Settings ---
      let cell-width = 0.5
      let height = 0.5
      let text-size = 0.5em
      let label-size = 8pt
      let tick-height = 0.15
      let tick-style = (paint: black, thickness: 0.5pt)

      let color-static = white

      // Calculate total bits (only used for auto-indexing start point)
      let total-bits = fields.map(f => f.bits).sum()
      let current-bit-idx = total-bits - 1
      let x = 0

      for field in fields {
        let w = field.bits * cell-width
        // Auto-calculate indices
        let auto-msb = str(current-bit-idx)
        let auto-lsb = str(current-bit-idx - field.bits + 1)

        // Use manual override if provided, otherwise use auto
        let disp-msb = if "msb" in field { str(field.msb) } else { auto-msb }
        let disp-lsb = if "lsb" in field and field.lsb != none { str(field.lsb) } else { auto-lsb }

        let field-type = field.at("type", default: "var")
        let bg-color = if "bg" in field {
          field.bg
        } else if field-type == "var" {
          _string_to_color(field.content)
        } else {
          color-static
        }

        // 1. Main Box
        rect((x, 0), (x + w, height), stroke: 1pt + black, fill: bg-color)

        // 2. Ticks
        if field.bits > 1 {
          for i in range(1, field.bits) {
            let tick-x = x + (i * cell-width)
            line((tick-x, 0), (tick-x, height * tick-height), stroke: tick-style)
            line((tick-x, height), (tick-x, height * (1 - tick-height)), stroke: tick-style)
          }
        }

        // 3. Content
        if field-type == "static" {
          let clean-content = field.content.replace(" ", "")
          for i in range(field.bits) {
             let bit-center-x = x + (i * cell-width) + (cell-width / 2)
             if i < clean-content.len() {
                content(
                  (bit-center-x, height/2),
                  text(size: text-size, fill: fg)[#clean-content.at(i)]
                )
             }
          }
        } else {
          content((x + w/2, height/2), text(size: text-size, fill: fg)[#field.content])
        }

        // 4. Label
        if "label" in field {
          content((x + w/2, -0.4), text(size: label-size, weight: "bold")[#field.label])
        }

        // 5. Indices (MSB and LSB)
        // Draw MSB (Top-Left)
        content(
          (x + 0.05, height + 0.1), anchor: "south-west",
          text(size: label-size)[#disp-msb]
        )
        // Draw LSB (Top-Right) only if width > 1 bit
        if field.bits > 1 {
          content(
            (x + w - 0.05, height + 0.1), anchor: "south-east",
            text(size: label-size)[#disp-lsb]
          )
        }

        // Advance coordinates
        x += w
        current-bit-idx -= field.bits
      }
    })
  ]
}

// Function to parse a single string like "[31:20](label)" or "[10]"
#let _parse_indice(raw-str) = {
  // Regex pattern matching the python version:
  // Group 1: First Number
  // Group 2: Second Number (Optional, behind colon)
  // Group 3: Label content (Optional, inside parens)
  let pat = regex("^\[(\d+)(?::(\d+))?\](?:\((.+)\))?$")

  let m = raw-str.match(pat)
  if m == none {
    panic("Invalid format: " + raw-str)
  }

  let num1 = int(m.captures.at(0))
  let num2-str = m.captures.at(1) // Can be none
  let label = m.captures.at(2)    // Can be none

  let msb = num1
  let lsb = none

  if num2-str != none {
    // Format was [msb:lsb]
    msb = num1
    lsb = int(num2-str)
  }

  // Return the Indice object (Dictionary)
  (
    msb: msb,
    lsb: lsb,
    label: label
  )
}

#let _parse_yaml_fields(fields) = {
  let result-fields = ()

  for item in fields {
    // item is a dictionary, e.g., (rs1: "[19:15]")
    // We iterate over the dictionary (usually just one key)
    for (key, val) in item {
      let content = key
      let inputs = ()

      // Normalize string vs array
      if type(val) == array {
        inputs = val
      } else {
        inputs.push(val)
      }

      let parsed-indices = inputs.map(_parse_indice)

      result-fields.push((
        content: content,
        indice: parsed-indices
      ))
    }
  }

  result-fields
}

#let parse_bits_groups(raw_str) = {
  let groups = ()
  let raw_len = raw_str.len()

  // Regex to find contiguous sequences of '0' or '1'
  let pat = regex("[01]+")

  // matches returns an array of match objects containing .start, .end, and .text
  let matches = raw_str.matches(pat)

  for m in matches {
    let len = m.text.len()

    // MSB: Left index (start of match)
    // LSB: Right index (end of match, inclusive)
    // Note: m.end is exclusive in Typst, so we subtract 1 for the inclusive index
    groups.push((
      bits: len,
      content: m.text,
      msb: raw_len - m.start - 1,
      lsb: raw_len - m.end,
      type: "static"
    ))
  }

  groups
}

#let draw_instruction(data, scale-factor: 1) = {
  assert("encoding" in data and data.encoding != none, message: "No encoding in data")
  assert("fields" in data and data.fields != none, message: "No fields in data")

  let data_fields = _parse_yaml_fields(data.fields)
  let entries = parse_bits_groups(data.encoding)

  for f in data_fields {
    for indice in f.indice {
      let bits = if indice.lsb != none {
        (indice.msb - indice.lsb) + 1
      } else {
        1
      }
      entries.push((bits: bits, content: f.content, msb: indice.msb, lsb: indice.lsb, label: indice.label))
    }
  }


  draw_bitvector(entries.sorted(key: k => k.msb, by: (l, r) => l > r), scale-factor: scale-factor)
}
