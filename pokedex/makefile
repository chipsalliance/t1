docs/rendered.pdf: doc.typ
	@mkdir -p docs
	@typst compile doc.typ $@

README.md: docs/doc.typ
	@typst compile --features html -f html $< readme.html
	@pandoc --from html --to markdown_strict readme.html -o $@
	@rm readme.html

.PHONY: doc
doc: README.md

.PHONY: doc-pdf
doc-pdf: docs/rendered.pdf

.PHONY: codegen
codegen:
	@mkdir -p build/nix build/generated
	@nix build ".#pokedex.codegen-cli" --out-link build/nix/codegen
	@nix build ".#t1.submodules.sources.riscv-opcodes.src" --out-link build/nix/riscv-opcodes
	@./build/nix/codegen/bin/codegen -d build/nix/riscv-opcodes -i ./model -o build/generated

.PHONY: clean
clean:
	@rm -rf build
