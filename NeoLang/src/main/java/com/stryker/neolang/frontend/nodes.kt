package io.neolang.frontend

import io.neolang.runtime.NeoLangValue

open class NeoLangAst {
  fun visit(): VisitorFactory {
    return VisitorFactory(this)
  }
}

open class NeoLangBaseNode : NeoLangAst()

class NeoLangArrayNode(val arrayNameNode: NeoLangStringNode, val elements: Array<ArrayElement>) : NeoLangBaseNode() {
  companion object {
    class ArrayElement(val index: Int, val block: NeoLangBlockNode)
  }
}

open class NeoLangAstBasedNode(val ast: NeoLangBaseNode) : NeoLangBaseNode() {
  override fun toString(): String {
    return "${javaClass.simpleName} { ast: $ast }"
  }
}

class NeoLangAttributeNode(val stringNode: NeoLangStringNode, val blockNode: NeoLangBlockNode) : NeoLangBaseNode() {

  override fun toString(): String {
    return "NeoLangAttributeNode { stringNode: $stringNode, block: $blockNode }"
  }
}

class NeoLangBlockNode(blockElement: NeoLangBaseNode) : NeoLangAstBasedNode(blockElement) {
  companion object {
    fun emptyNode(): NeoLangBlockNode {
      return NeoLangBlockNode(NeoLangDummyNode())
    }
  }
}

class NeoLangDummyNode : NeoLangBaseNode()

class NeoLangGroupNode(val attributes: Array<NeoLangAttributeNode>) : NeoLangBaseNode() {

  override fun toString(): String {
    return "NeoLangGroupNode { attrs: $attributes }"
  }

  companion object {
    fun emptyNode(): NeoLangGroupNode {
      return NeoLangGroupNode(arrayOf())
    }
  }
}

class NeoLangNumberNode(token: NeoLangToken) : NeoLangTokenBasedNode(token)

class NeoLangProgramNode(val groups: List<NeoLangGroupNode>) : NeoLangBaseNode() {

  override fun toString(): String {
    return "NeoLangProgramNode { groups: $groups }"
  }

  companion object {
    fun emptyNode(): NeoLangProgramNode {
      return NeoLangProgramNode(listOf())
    }
  }
}

class NeoLangStringNode(token: NeoLangToken) : NeoLangTokenBasedNode(token)

open class NeoLangTokenBasedNode(val token: NeoLangToken) : NeoLangBaseNode() {
  override fun toString(): String {
    return "${javaClass.simpleName} { token: $token }"
  }

  fun eval(): NeoLangValue {
    return token.tokenValue.value
  }
}
