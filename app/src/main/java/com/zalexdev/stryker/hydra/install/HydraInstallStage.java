package com.zalexdev.stryker.hydra.install;

import com.zalexdev.stryker.R;

public enum HydraInstallStage {
    REFRESH(R.string.hydra_install_stage_refresh),
    INSTALL(R.string.hydra_install_stage_install),
    VERIFY(R.string.hydra_install_stage_verify);

    public final int titleRes;

    HydraInstallStage(int titleRes) {
        this.titleRes = titleRes;
    }
}
