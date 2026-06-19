package com.zalexdev.stryker.nuclei.install;

import com.zalexdev.stryker.R;

public enum NucleiInstallStage {
    PREPARE(R.string.nuclei_install_stage_prepare),
    REFRESH(R.string.nuclei_install_stage_refresh),
    INSTALL_GO(R.string.nuclei_install_stage_install_go),
    GO_BUILD(R.string.nuclei_install_stage_go_build),
    DEPLOY(R.string.nuclei_install_stage_deploy),
    VERIFY(R.string.nuclei_install_stage_verify);

    public final int titleRes;

    NucleiInstallStage(int titleRes) {
        this.titleRes = titleRes;
    }
}
