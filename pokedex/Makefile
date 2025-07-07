docs/doc.pdf: docs/doc.typ
	@mkdir -p docs
	typst compile $< $@

README.md: docs/doc.typ
	@typst compile --features html -f html $< readme.html
	@pandoc --from html --to markdown_strict readme.html -o $@
	@rm readme.html

.PHONY: doc
doc: README.md
	@git add docs/doc.typ $<

.PHONY: doc-pdf
doc-pdf: docs/doc.pdf
