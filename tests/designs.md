---
start-date: 2025-01-18
author: Avimitin
---

# Summary
[summary]: #summary

This document is here to describe the new Nix build system designs for T1 test
cases category. It aims to provide a tests framework that integrate all the T1
compiler, emulator, linker script...etc to one environment, and any developer,
even those who doesn't know how Nix works can easily get their code working on
T1 emulator.

# Motivation
[motivation]: #motivation

T1 RTLs is now stable and the needs for more contributor to support more
variety of code cases is increasing. Our current Nix build system is design for
internal usage. For software developer, they probably wants a project that
works like Spike or QEMU which contains CLI toolchains that are easy to use and
doesn't have the requirement of code and framework design to run their code.
Also the CLI interface should be simple because most of them hate reading manual.
Our current workflow doesn't well support that.

Besides, although our Nix framework does have scalability, it contains too much
tech debt and even the best Nix code writer in our team will need to spend some
time to understand the complex build system design. We need to modify the
framework to be more straightforward, before we can't handle it.

# Detailed design
[design]: #detailed-design

According the motivation part, we have two goals to achieve:

* Outside contributors can easily run and integrate their application to T1 project.
* A new scalable test framework that are easy to maintain.

## Framework Designs

### Machine requirement

As T1 contains multiple TOPs and configs to support, the code may or may not
runs on some of the combilation. For example, codes that contains floating
number calculation can't runs on config without Zve32f extension, and some
codes might required larger VLEN to have better performance. We should give
user a fatal error when they use incompatible config to run their code and a
warning when they run with config that doesn't support the application quite
well.

The old T1 test framework did support this feature. Each test case optionally
provides a `features-required` field to specify what config should be provided.
The top config level will provides current selection detail, and a middleware will
check if the requirement match the current selection.

Nixpkgs also contains similar logic for target host check:
derivations declared their target host requirement by field `meta.platform`,
And the Nixpkgs library provide a function `availableOn`

# Examples and Interactions
[examples-and-interactions]: #examples-and-interactions

This section illustrates the detailed design.
This section should clarify all confusion the reader has from the previous sections.
It is especially important to counterbalance the desired terseness of the detailed design;
if you feel your detailed design is rudely short, consider making this section longer instead.

# Drawbacks
[drawbacks]: #drawbacks

What are the disadvantages of doing this?

# Alternatives
[alternatives]: #alternatives

What other designs have been considered? What is the impact of not doing this?
For each design decision made, discuss possible alternatives and compare them to the chosen solution.
The reader should be convinced that this is indeed the best possible solution for the problem at hand.

# Prior art
[prior-art]: #prior-art

You are unlikely to be the first one to tackle this problem.
Try to dig up earlier discussions around the topic or prior attempts at improving things.
Summarize, discuss what was good or bad, and compare to the current proposal.
If applicable, have a look at what other projects and communities are doing.
You may also discuss related work here, although some of that might be better located in other sections.

# Unresolved questions
[unresolved]: #unresolved-questions

What parts of the design are still TBD or unknowns?

# Future work
[future]: #future-work

What future work, if any, would be implied or impacted by this feature without being directly part of the work? 
