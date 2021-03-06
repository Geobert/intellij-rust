package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.lang.core.psi.ext.resolveToTrait
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.findImplsAndTraits
import org.rust.lang.core.types.infer.remapTypeParameters
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type
import org.rust.lang.core.types.ty.TyUnknown

fun findDerefTarget(project: Project, ty: Ty): Ty? {
    val impls = RsImplIndex.findImpls(project, ty)
    for (impl in impls) {
        val trait = impl.traitRef?.resolveToTrait ?: continue
        if (!trait.isDeref) continue
        return lookupAssociatedType(impl, "Target")
    }
    return null
}

fun findIteratorItemType(project: Project, ty: Ty): Ty {
    val impl = findImplsAndTraits(project, ty).first
        .find { boundImpl ->
            val traitName = boundImpl.element.traitRef?.resolveToTrait?.name
            traitName == "Iterator" || traitName == "IntoIterator"
        } ?: return TyUnknown

    val rawType = lookupAssociatedType(impl.element, "Item")
    return rawType.substitute(impl.typeArguments)
}

fun findIndexOutputType(project: Project, containerType: Ty, indexType: Ty): Ty {
    val impls = RsImplIndex.findImpls(project, containerType)
        .filter { it.traitRef?.resolveToTrait?.isIndex ?: false }

    val suitableImpl = if (impls.size < 2) {
        impls.firstOrNull()
    } else {
        impls.find { impl ->
            // TODO: get index type from impl declaration
            impl.functionList
                .find { it.name == "index" }
                ?.valueParameterList
                ?.valueParameterList
                // 'index' function have only one value parameter
                // fn index(&self, index: Idx) -> &Self::Output;
                ?.getOrNull(0)
                ?.typeReference
                ?.type
                ?.canUnifyWith(indexType, project) ?: false
        }
    } ?: return TyUnknown

    val rawOutputType = lookupAssociatedType(suitableImpl, "Output")
    val typeParameterMap = suitableImpl.remapTypeParameters(containerType.typeParameterValues)
    return TyReference(rawOutputType.substitute(typeParameterMap))
}

private val RsTraitItem.langAttribute: String? get() {
    if (this.stub != null) return this.stub.langAttribute
    return this.queryAttributes.langAttribute
}

private val RsTraitItem.isDeref: Boolean get() = langAttribute == "deref"
private val RsTraitItem.isIndex: Boolean get() = langAttribute == "index"

private fun lookupAssociatedType(impl: RsImplItem, name: String): Ty =
    impl.typeAliasList.find { it.name == name }?.typeReference?.type
        ?: TyUnknown
