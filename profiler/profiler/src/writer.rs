use crate::{
    events::{CheckRd, Inst, Issue, LsuEnq, VrfScoreboardReport},
    fst_writer::{self, FstResult, HandleLogicBus, Writer},
    parse::{EventPack, Process},
    Config,
};

const PERIOD: u64 = 10;

struct HandleLsuEnq {
    enq: HandleLogicBus,
}

impl HandleLsuEnq {
    pub fn new(writer: &mut Writer, prefix: &str) -> FstResult<Self> {
        let enq = writer.create_var_u32(&format!("{prefix}_enq"))?;
        Ok(Self { enq })
    }
    pub fn write_data(&self, writer: &mut Writer, data: &LsuEnq) -> FstResult<()> {
        self.enq.write_u32(writer, data.enq);
        Ok(())
    }
    pub fn write_x(&self, writer: &mut Writer) -> FstResult<()> {
        Ok(())
    }
}

struct HandleIssue {
    idx: HandleLogicBus,
}

impl HandleIssue {
    pub fn new(writer: &mut Writer, prefix: &str) -> FstResult<Self> {
        let idx = writer.create_var_u8(&format!("{prefix}_idx"))?;
        Ok(Self { idx })
    }
    pub fn write_data(&self, writer: &mut Writer, data: &Issue) -> FstResult<()> {
        self.idx.write_u8(writer, data.idx)?;
        Ok(())
    }
    pub fn write_x(&self, writer: &mut Writer) -> FstResult<()> {
        self.idx.write_x(writer)?;
        Ok(())
    }
}

struct HandleCheckRd {
    data: HandleLogicBus,
    issue_idx: HandleLogicBus,
}

impl HandleCheckRd {
    pub fn new(writer: &mut Writer, prefix: &str) -> FstResult<Self> {
        let data = writer.create_var_u32(&format!("{prefix}_data"))?;
        let issue_idx = writer.create_var_u8(&format!("{prefix}_issue_idx"))?;
        Ok(Self { data, issue_idx })
    }
    pub fn write_data(&self, writer: &mut Writer, data: &CheckRd) -> FstResult<()> {
        self.data.write_u32(writer, data.data)?;
        self.issue_idx.write_u8(writer, data.issue_idx)?;
        Ok(())
    }
    pub fn write_x(&self, writer: &mut Writer) -> FstResult<()> {
        self.data.write_x(writer)?;
        self.issue_idx.write_x(writer)?;
        Ok(())
    }
}

struct HandleVrfScoreboardReport {
    count: HandleLogicBus,
    issue_idx: HandleLogicBus,
}

impl HandleVrfScoreboardReport {
    pub fn new(writer: &mut Writer, prefix: &str) -> FstResult<Self> {
        let count = writer.create_var_u32(&format!("{prefix}_count"))?;
        let issue_idx = writer.create_var_u8(&format!("{prefix}_issue_idx"))?;
        Ok(Self { count, issue_idx })
    }
    pub fn write_data(&self, writer: &mut Writer, data: &VrfScoreboardReport) -> FstResult<()> {
        self.count.write_u32(writer, data.count)?;
        self.issue_idx.write_u8(writer, data.issue_idx)?;
        Ok(())
    }
    pub fn write_x(&self, writer: &mut Writer) -> FstResult<()> {
        self.count.write_x(writer)?;
        self.issue_idx.write_x(writer)?;
        Ok(())
    }
}

struct HandleInst {
    data: HandleLogicBus,
}

impl HandleInst {
    pub fn new(writer: &mut Writer, prefix: &str) -> FstResult<Self> {
        let data = writer.create_var_u32(&format!("{prefix}_data"))?;
        Ok(Self { data })
    }
    pub fn write_data(&self, writer: &mut Writer, data: &Inst) -> FstResult<()> {
        self.data.write_u32(writer, data.data);
        Ok(())
    }
    pub fn write_x(&self, writer: &mut Writer) -> FstResult<()> {
        self.data.write_x(writer)?;
        Ok(())
    }
}

pub struct Handler {
    config: Config,
    fst_writer: Writer,
    var_sim_time: HandleLogicBus,
    var_issue: HandleIssue,
    var_lsuenq: HandleLsuEnq,
    var_check_rd: HandleCheckRd,
    var_vrf_scoreboard_report: HandleVrfScoreboardReport,
    sim_time: u64,
}

impl Handler {
    pub fn new(config: Config, mut fst_writer: Writer) -> anyhow::Result<Self> {
        let var_sim_time;
        let var_issue;
        let var_lsuenq;
        let var_check_rd;
        let var_vrf_scoreboard_report;
        {
            let w = &mut fst_writer;
            w.scope("EVENT")?;

            var_sim_time = w.create_var_u64("CYCLE")?;
            var_issue = HandleIssue::new(w, "issue")?;
            var_lsuenq = HandleLsuEnq::new(w, "lsuenq")?;
            var_check_rd = HandleCheckRd::new(w, "checkrd")?;
            var_vrf_scoreboard_report = HandleVrfScoreboardReport::new(w, "vrfscoreboardreport")?;

            w.upscope();
        }
        Ok(Self {
            config,
            fst_writer,
            var_sim_time,
            var_issue,
            var_lsuenq,
            var_check_rd,
            var_vrf_scoreboard_report,
            sim_time: 0,
        })
    }

    pub fn write_x(&mut self) -> FstResult<()> {
        let w = &mut self.fst_writer;
        self.var_issue.write_x(w)?;
        self.var_lsuenq.write_x(w)?;
        self.var_check_rd.write_x(w)?;
        self.var_vrf_scoreboard_report.write_x(w)?;
        Ok(())
    }
}

impl Process for Handler {
    fn run_cycle(&mut self, cycle: u64, events: &EventPack) -> anyhow::Result<()> {
        while self.sim_time < cycle {
            self.write_x().unwrap();

            self.sim_time += 1;
            self.fst_writer.set_time(self.sim_time * PERIOD);
            self.var_sim_time
                .write_u64(&mut self.fst_writer, self.sim_time)
                .unwrap();
        }

        let w = &mut self.fst_writer;
        if let Some(data) = &events.issue {
            self.var_issue.write_data(w, data).unwrap();
        } else {
            self.var_issue.write_x(w).unwrap();
        }
        if let Some(data) = &events.lsuenq {
            self.var_lsuenq.write_data(w, data).unwrap();
        } else {
            self.var_lsuenq.write_x(w).unwrap();
        }
        if let Some(data) = &events.check_rd {
            self.var_check_rd.write_data(w, data).unwrap();
        } else {
            self.var_check_rd.write_x(w).unwrap();
        }
        if let Some(data) = &events.vrf_scoreboard_report {
            self.var_vrf_scoreboard_report.write_data(w, data).unwrap();
        } else {
            self.var_vrf_scoreboard_report.write_x(w).unwrap();
        }

        self.sim_time += 1;
        self.fst_writer.set_time(self.sim_time * PERIOD);
        self.var_sim_time
            .write_u64(&mut self.fst_writer, self.sim_time)
            .unwrap();

        // println!("cycle={cycle}, {events:?}");

        Ok(())
    }

    fn finish(&mut self, cycle: u64) -> anyhow::Result<()> {
        self.fst_writer.flush();
        Ok(())
    }
}

pub fn self_test() -> anyhow::Result<()> {
    let mut w = Writer::new("wave.fst")?;
    let w = &mut w;
    w.scope("TOP")?;
    let var1 = w.create_var_logic("aaa")?;
    let var2 = w.create_var_string("bbb", 10)?;
    w.upscope();

    var1.write_bool(w, true)?;
    var2.write_str(w, "Hello")?;

    w.set_time(10);
    var1.write_x(w)?;
    var2.write_x(w)?;

    w.set_time(20);
    var1.write_bool(w, false)?;
    var2.write_str(w, "World")?;

    w.set_time(30);
    Ok(())
}
