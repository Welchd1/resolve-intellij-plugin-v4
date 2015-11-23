package edu.clemson.resolve.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.antlr.intellij.adaptor.parser.PsiElementFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResMathPrefixDefSigImpl extends ResAbstractMathDefSig {

    public ResMathPrefixDefSigImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Nullable @Override public PsiElement getIdentifier() {
        return findChildByType(ResJetbrainTypes.ID);
    }

    public static class Factory implements PsiElementFactory {
        public static Factory INSTANCE = new Factory();

        @Override public PsiElement createElement(ASTNode node) {
            return new ResMathPrefixDefSigImpl(node);
        }
    }
}
