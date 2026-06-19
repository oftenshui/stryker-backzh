package com.stryker.terminal.frontend.session.view.extrakey

class CombinedSequence private constructor() {
  val keys = mutableListOf<String>()

  companion object {
    fun solveString(keyString: String): CombinedSequence {
      val seq = CombinedSequence()
      keyString.split(' ').forEach {
        val key = if (it.startsWith('<') && it.endsWith('>')) {
          it.substring(1, it.length - 1)
        } else {
          it
        }
        seq.keys.add(key)
      }
      return seq
    }
  }
}
