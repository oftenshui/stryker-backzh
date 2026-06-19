package com.zalexdev.stryker.metasploit.install;

import com.zalexdev.stryker.R;

public enum MsfInstallStage {
    REFRESH_INDEX(R.string.msf_stage_index),
    DEPS(R.string.msf_stage_deps),
    CLONE(R.string.msf_stage_clone),
    MSFPC(R.string.msf_stage_msfpc),
    LINK(R.string.msf_stage_link),
    BUNDLER(R.string.msf_stage_bundler),
    BUNDLE(R.string.msf_stage_bundle),
    WARM(R.string.msf_stage_warm),
    VERIFY(R.string.msf_stage_verify);

    public final int titleRes;

    MsfInstallStage(int titleRes) {
        this.titleRes = titleRes;
    }
}
