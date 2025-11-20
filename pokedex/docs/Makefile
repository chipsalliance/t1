docs: doc.pdf doc-dark.pdf

doc-dark.pdf: doc.typ
	typst compile --input enable-darkmode=true $< $@

doc.pdf: doc.typ
	typst compile $< $@
