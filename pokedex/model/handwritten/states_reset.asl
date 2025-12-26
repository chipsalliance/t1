// export to simulator
func ASL_ResetConfigAndState()
begin
  initConfigDefault();
  // states.asl
  resetArchStateDefault();
  // states_v.asl
  resetVectorState();
  // states_s_mode.asl
  resetArchStateSMode();
end

// export to simulator
func ASL_ResetState()
begin
  resetArchStateDefault();
  resetVectorState();
end
