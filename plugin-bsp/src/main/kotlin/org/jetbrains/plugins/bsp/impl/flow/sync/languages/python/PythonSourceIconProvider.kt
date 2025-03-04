package org.jetbrains.plugins.bsp.impl.flow.sync.languages.python

import com.jetbrains.python.parser.icons.PythonParserIcons
import org.jetbrains.plugins.bsp.utils.SourceType
import org.jetbrains.plugins.bsp.utils.SourceTypeIconProvider
import javax.swing.Icon

class PythonSourceIconProvider : SourceTypeIconProvider {
  override fun getIcon(): Icon = PythonParserIcons.PythonFile

  override fun getSourceType(): SourceType = SourceType.PYTHON
}
