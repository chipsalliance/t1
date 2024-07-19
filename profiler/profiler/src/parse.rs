use crate::events::{CheckRd, Event, EventWithTime, Issue, LsuEnq, VrfScoreboardReport, VrfWrite};
use std::io;

pub fn process(input: &mut dyn io::BufRead, handler: &mut dyn Process) -> anyhow::Result<()> {
    let mut cycle = 0;
    let mut events = EventPack::new();

    let mut buf = String::new();
    while let Some(line) = read_line(input, &mut buf)? {
        let line = line.trim();
        if line.is_empty() {
            continue;
        }

        let event = serde_json::from_str::<EventWithTime>(line)?;
        if cycle != event.cycle {
            assert!(cycle < event.cycle, "cycle is not monotone");
            handler.run_cycle(cycle, &events)?;

            events.clear();
            cycle = event.cycle;
        }

        match event.event {
            Event::SimulationStart(_) => {
                // ignored
            }
            Event::SimulationStop(_) => {
                // ignored
            }
            Event::Issue(e) => {
                assert!(
                    events.issue.is_none(),
                    "multiple 'Issue' event at [{cycle}]"
                );
                events.issue = Some(e);
            }
            Event::LsuEnq(e) => {
                assert!(
                    events.lsuenq.is_none(),
                    "multiple 'LsuEnq' event at [{cycle}]"
                );
                events.lsuenq = Some(e);
            }
            Event::VrfWrite(e) => {
                events.vrf_writes.push(e);
            }
            Event::MemoryWrite(_) => {
                // TODO
            }
            Event::CheckRd(_) => {
                // TODO
            }
            Event::VrfScoreboardReport(e) => {
                assert!(
                    events.vrf_scoreboard_report.is_none(),
                    "multiple 'VrfScoreBoardReport' event at [{cycle}]"
                );
                events.vrf_scoreboard_report = Some(e);
            }
        }
    }

    handler.run_cycle(cycle, &events)?;
    handler.finish(cycle)?;

    Ok(())
}

fn read_line<'buf>(
    input: &mut dyn io::BufRead,
    buf: &'buf mut String,
) -> io::Result<Option<&'buf String>> {
    buf.clear();
    if input.read_line(buf)? == 0 {
        Ok(None) // EOF
    } else {
        Ok(Some(buf))
    }
}

pub trait Process {
    fn run_cycle(&mut self, cycle: u64, events: &EventPack) -> anyhow::Result<()>;
    fn finish(&mut self, cycle: u64) -> anyhow::Result<()>;
}

#[derive(Debug, Default)]
pub struct EventPack {
    pub issue: Option<Issue>,
    pub lsuenq: Option<LsuEnq>,
    pub check_rd: Option<CheckRd>,
    pub vrf_scoreboard_report: Option<VrfScoreboardReport>,
    vrf_writes: Vec<VrfWrite>,
}

impl EventPack {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn clear(&mut self) {
        self.issue = None;
        self.lsuenq = None;
        self.check_rd = None;
        self.vrf_scoreboard_report = None;
        self.vrf_writes.clear();
    }
}
