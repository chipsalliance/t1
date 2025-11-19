default:
	@rm -f ./doc-comments.yml
	@nix develop '.#pokedex.docs' -c bash -c 'source $$stdenv/setup; runPhase buildPhase'
	@mv -v *.pdf ./display/

prepare:
	@nix build '.#pokedex.model' --out-link model
	@cp -vf ./model/docs/doc-comments.yml .
	@rm ./model
