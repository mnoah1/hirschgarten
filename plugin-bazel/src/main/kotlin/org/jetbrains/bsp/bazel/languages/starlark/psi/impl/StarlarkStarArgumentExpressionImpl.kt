package org.jetbrains.bsp.bazel.languages.starlark.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import org.jetbrains.bsp.bazel.languages.starlark.psi.StarlarkStarArgumentExpression

class StarlarkStarArgumentExpressionImpl(node: ASTNode) : ASTWrapperPsiElement(node), StarlarkStarArgumentExpression
