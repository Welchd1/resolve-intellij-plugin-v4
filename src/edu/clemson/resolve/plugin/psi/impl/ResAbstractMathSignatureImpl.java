package edu.clemson.resolve.plugin.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import edu.clemson.resolve.plugin.ResTypes;
import edu.clemson.resolve.plugin.psi.ResMathDefinitionSignature;
import edu.clemson.resolve.plugin.psi.ResMathExp;
import edu.clemson.resolve.plugin.psi.ResMathVarDeclGroup;
import edu.clemson.resolve.plugin.psi.ResType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ResAbstractMathSignatureImpl
        extends
            ResNamedElementImpl implements ResMathDefinitionSignature {

    public ResAbstractMathSignatureImpl(@NotNull ASTNode node) {
        super(node);
    }

    @NotNull @Override public List<ResMathVarDeclGroup> getParameters() {
        return PsiTreeUtil.getChildrenOfTypeAsList(this,
                ResMathVarDeclGroup.class);
    }

    @Nullable @Override public ResMathExp getMathTypeExp() {
        return findChildByClass(ResMathExp.class);
    }

    @Nullable @Override public PsiElement getIdentifier() {
        return findChildByType(ResTypes.MATH_NAME_IDENTIFIER_SYMBOL);
    }
}
