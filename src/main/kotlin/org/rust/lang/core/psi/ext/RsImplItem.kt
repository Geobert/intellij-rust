package org.rust.lang.core.psi.ext

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.stubs.RsImplItemStub

abstract class RsImplItemImplMixin : RsStubbedElementImpl<RsImplItemStub>, RsImplItem {

    constructor(node: ASTNode) : super(node)
    constructor(stub: RsImplItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int) = RsIcons.IMPL

    override val isPublic: Boolean get() = false // pub does not affect imls at all

    override fun getPresentation(): ItemPresentation {
        val t = typeReference
        if (t is RsBaseType) {
            val pres = (t.path?.reference?.resolve() as? RsNamedElement)?.presentation
            if (pres != null) {
                return PresentationData(pres.presentableText, pres.locationString, RsIcons.IMPL, null)
            }
        }
        return PresentationData(typeReference?.text ?: "Impl", null, RsIcons.IMPL, null)
    }
}

/**
 * @return pair of two lists: (mandatory trait members, optional trait members)
 */
fun RsImplItem.toImplementOverride(resolvedTrait: RsTraitItem? = null): Pair<List<RsNamedElement>, List<RsNamedElement>>? {
    val trait = resolvedTrait ?: traitRef?.resolveToTrait ?: return null
    val traitMembers = trait.children.filterIsInstance<RsAbstractable>()
    val members = children.filterIsInstance<RsAbstractable>()
    val canImplement = traitMembers.associateBy { it.name }
    val mustImplement = canImplement.filterValues { it.isAbstract }
    val implemented = members.associateBy { it.name }
    val notImplemented = mustImplement.keys - implemented.keys
    val toImplement = traitMembers.filter { it.name in notImplemented }

    return toImplement to traitMembers
}
