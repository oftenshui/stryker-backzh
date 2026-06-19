package com.stryker.terminal.ui.term

import com.stryker.terminal.backend.TerminalSession
import com.stryker.terminal.component.session.XSession
import com.stryker.terminal.services.NeoTermService

object SessionRemover {
  fun removeSession(termService: NeoTermService?, tab: TermTab) {
    tab.termData.termSession?.finishIfRunning()
    removeFinishedSession(termService, tab.termData.termSession)
    tab.cleanup()
  }

  fun removeXSession(termService: NeoTermService?, tab: XSessionTab?) {
    removeFinishedSession(termService, tab?.session)
  }

  private fun removeFinishedSession(termService: NeoTermService?, finishedSession: TerminalSession?) {
    if (termService == null || finishedSession == null) {
      return
    }

    termService.removeTermSession(finishedSession)
  }

  private fun removeFinishedSession(termService: NeoTermService?, finishedSession: XSession?) {
    if (termService == null || finishedSession == null) {
      return
    }

    termService.removeXSession(finishedSession)
  }
}
