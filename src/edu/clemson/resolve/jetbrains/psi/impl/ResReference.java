package edu.clemson.resolve.jetbrains.psi.impl;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OrderedSet;
import edu.clemson.resolve.jetbrains.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ResReference extends PsiPolyVariantReferenceBase<ResReferenceExpBase> {

    private static final Key<SmartPsiElementPointer<ResReferenceExpBase>> CONTEXT = Key.create("CONTEXT");
    private static final Key<String> ACTUAL_NAME = Key.create("ACTUAL_NAME");

    private static final ResolveCache.PolyVariantResolver<PsiPolyVariantReferenceBase> MY_RESOLVER =
            new ResolveCache.PolyVariantResolver<PsiPolyVariantReferenceBase>() {
                @NotNull
                @Override
                public ResolveResult[] resolve(@NotNull PsiPolyVariantReferenceBase psiPolyVariantReferenceBase,
                                               boolean incompleteCode) {
                    return ((ResReference) psiPolyVariantReferenceBase).resolveInner();
                }
            };

    @NotNull
    private PsiElement getIdentifier() {
        return myElement.getIdentifier();
    }

    ResReference(@NotNull ResReferenceExpBase o) {
        super(o, TextRange.from(o.getIdentifier().getStartOffsetInParent(), o.getIdentifier().getTextLength()));
    }

    @NotNull
    private ResolveResult[] resolveInner() {
        if (!myElement.isValid()) return ResolveResult.EMPTY_ARRAY;
        Collection<ResolveResult> result = new OrderedSet<ResolveResult>();
        processResolveVariants(createResolveProcessor(result, myElement));
        return result.toArray(new ResolveResult[result.size()]);
    }

    @NotNull
    static ResScopeProcessor createResolveProcessor(@NotNull final Collection<ResolveResult> result,
                                                    @NotNull final ResReferenceExpBase o) {
        return new ResScopeProcessor() {
            @Override
            public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
                if (element.equals(o)) {
                    return !result.add(new PsiElementResolveResult(element));
                }
                String name = ObjectUtils.chooseNotNull(state.get(ACTUAL_NAME),
                        element instanceof PsiNamedElement ? ((PsiNamedElement) element).getName() : null);
                if (name != null && o.getIdentifier().textMatches(name)) {
                    result.add(new PsiElementResolveResult(element));
                    return false;
                }
                return true;
            }
        };
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        if (!myElement.isValid()) return ResolveResult.EMPTY_ARRAY;
        return ResolveCache.getInstance(myElement.getProject()).resolveWithCaching(this, MY_RESOLVER, false, false);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public boolean processResolveVariants(@NotNull ResScopeProcessor processor) {
        PsiFile file = myElement.getContainingFile();
        if (!(file instanceof ResFile)) return false;
        ResolveState state = ResolveState.initial();
        ResReferenceExpBase qualifier = myElement.getQualifier();
        return qualifier != null ?
                processQualifierExpression(((ResFile) file), qualifier, processor, state) :
                processUnqualifiedResolve(((ResFile) file), processor, state, true);
    }

    public static boolean processQualifierExpression(@NotNull ResFile file,
                                                     @NotNull ResReferenceExpBase qualifier,
                                                     @NotNull ResScopeProcessor processor,
                                                     @NotNull ResolveState state) {
        PsiReference reference = qualifier.getReference();
        PsiElement target = reference != null ? reference.resolve() : null;
        if (target == null || target == qualifier) return false;
        if (target instanceof ResFacilityDecl) {
            ResFacilityDecl facility = ((ResFacilityDecl) target);
            ResFile resolvedSpec = facility.resolveSpecification();
            if (resolvedSpec != null) processModuleLevelEntities(resolvedSpec, processor, state, false, true);

           /* for (ResExtensionPairing p : facility.getExtensionPairingList()) {
                if (p.getModuleSpecList().isEmpty()) continue;
                ResFile spec = (ResFile) p.getModuleSpecList().get(0).resolve();
                if (spec == null) continue;
                processModuleLevelEntities(spec, processor, state, false);
            }*/
        }
        else if (target instanceof ResModuleIdentifierSpec) {
            PsiElement e = ((ResModuleIdentifierSpec) target).getModuleIdentifier().resolve();
            if (e != null && e instanceof ResFile) {
                processModuleLevelEntities((ResFile) e, processor, state, false, false);
            }
        }
        else if (target instanceof ResFile) {
            processModuleLevelEntities((ResFile) target, processor, state, false, false);
        }
        return true;
    }

    private boolean processUnqualifiedResolve(@NotNull ResFile file,
                                              @NotNull ResScopeProcessor processor,
                                              @NotNull ResolveState state,
                                              boolean localResolve) {

        PsiElement parent = myElement.getParent();
        if (parent instanceof ResSelectorExp) {
            boolean result = processSelector((ResSelectorExp) parent, processor, state, myElement);
            if (processor.isCompletion()) return result;
            if (!result || ResPsiImplUtil.prevDot(myElement)) return false;
        }
        PsiElement grandPa = parent.getParent();
        if (grandPa instanceof ResSelectorExp && !processSelector((ResSelectorExp) grandPa, processor, state, parent)) return false;
        if (ResPsiImplUtil.prevDot(parent)) return false;
        if (!processBlock(processor, state, true)) return false;
        if (!processModuleLevelEntities(file, processor, state, true)) return false;
        if (!processUsesImports(file, processor, state)) return false;
        return true;
    }

    private boolean processSelector(@NotNull ResSelectorExp parent,
                                    @NotNull ResScopeProcessor processor,
                                    @NotNull ResolveState state,
                                    @Nullable PsiElement another) {
        List<ResExp> list = parent.getExpList();
        if (list.size() > 1 && list.get(1).isEquivalentTo(another)) {
            ResType type = list.get(0).getResType(createContext());
            if (type != null && !processResType(type, processor, state)) return false;
        }
        return true;
    }

    private boolean processResType(@NotNull ResType type,
                                   @NotNull ResScopeProcessor processor,
                                   @NotNull ResolveState state) {
        if (!processExistingType(type, processor, state)) return false;
        return processTypeRef(type, processor, state);
    }

    private boolean processTypeRef(@Nullable ResType type,
                                   @NotNull ResScopeProcessor processor,
                                   @NotNull ResolveState state) {
        return processInTypeRef(getTypeReference(type), type, processor, state);
    }

    private boolean processInTypeRef(@Nullable ResTypeReferenceExp refExp,
                                     @Nullable ResType recursiveStopper,
                                     @NotNull ResScopeProcessor processor,
                                     @NotNull ResolveState state) {
        PsiReference reference = refExp != null ? refExp.getReference() : null;
        PsiElement resolve = reference != null ? reference.resolve() : null;
        if (resolve instanceof ResTypeOwner) {
            ResType type = ((ResTypeOwner) resolve).getResType(state);
            if (type != null && !processResType(type, processor, state)) return false;
        }
        return true;
    }

    private boolean processExistingType(@NotNull ResType type,
                                        @NotNull ResScopeProcessor processor,
                                        @NotNull ResolveState state) {
        PsiFile file = type.getContainingFile();
        if (!(file instanceof ResFile)) return true;
        PsiFile myFile = ObjectUtils.notNull(getContextFile(state), myElement.getContainingFile());
        if (!(myFile instanceof ResFile)) return true;
        boolean localResolve = true;
        if (type instanceof ResTypeReprDecl) type = ((ResTypeReprDecl) type).getType();
        if (type instanceof ResRecordType) {
            ResScopeProcessorBase delegate = createDelegate(processor);
            type.processDeclarations(delegate, ResolveState.initial(), null, myElement);
            List<ResTypeReferenceExp> structRefs = ContainerUtil.newArrayList();
            for (ResRecordVarDeclGroup d : ((ResRecordType) type).getRecordVarDeclGroupList()) {
                if (!processNamedElements(processor, state, d.getFieldVarDeclGroup().getFieldDefList(), localResolve))
                    return false;
            }
            if (!processCollectedRefs(type, structRefs, processor, state)) return false;
        }
        return true;
    }

    private boolean processCollectedRefs(@NotNull ResType type, @NotNull List<ResTypeReferenceExp> refs,
                                         @NotNull ResScopeProcessor processor, @NotNull ResolveState state) {
        for (ResTypeReferenceExp ref : refs) {
            if (!processInTypeRef(ref, type, processor, state)) return false;
        }
        return true;
    }

    @Nullable
    public static ResTypeReferenceExp getTypeReference(@Nullable ResType o) {
        if (o == null) return null;
        return o.getTypeReferenceExp();
    }

    @NotNull
    public ResolveState createContext() {
        return ResolveState.initial().put(CONTEXT, SmartPointerManager.getInstance(myElement.getProject())
                .createSmartPsiElementPointer(myElement));
    }

    private boolean processBlock(@NotNull ResScopeProcessor processor,
                                 @NotNull ResolveState state,
                                 boolean localResolve) {
        ResScopeProcessorBase delegate = createDelegate(processor);
        ResolveUtil.treeWalkUp(myElement, delegate);

        //process local parameters if we're in a local definition or an operation like thing (doesn't include module
        //params; that's in processModuleLevelEntities)
        processBlockParameters(myElement, delegate);
        return processNamedElements(processor, state, delegate.getVariants(), localResolve);
    }

    //Go plugin rolls this step into the "treeWalkUp" phase -- see processParameters in GoCompositeElementImpl.
    //I just do it here for simplicity
    static boolean processBlockParameters(@NotNull ResCompositeElement e,
                                          @NotNull ResScopeProcessorBase processor) {
        ResMathDefnDecl def = PsiTreeUtil.getParentOfType(e, ResMathDefnDecl.class);
        ResOperationLikeNode operation = PsiTreeUtil.getParentOfType(e, ResOperationLikeNode.class);
        if (def != null) processDefinitionParams(processor, def);
        if (operation != null) processProgParamDecls(processor, operation.getParamDeclList());
        return true;
    }

    public static boolean processUsesImports(@NotNull ResFile file,
                                             @NotNull ResScopeProcessor processor,
                                             @NotNull ResolveState state) {
        return processUsesImports(file, processor, state, false);
    }

    private static boolean processUsesImports(@NotNull ResFile file,
                                             @NotNull ResScopeProcessor processor,
                                             @NotNull ResolveState state,
                                             boolean forSuperModules) {
        List<ResModuleIdentifierSpec> usesItems = file.getModuleIdentifierSpecs();
        for (ResModuleIdentifierSpec o : usesItems) {
            if (o.getAlias() != null) {
                if (!processor.execute(o, state.put(ACTUAL_NAME, o.getAlias().getText()))) return false;
            }
            else {
                PsiElement resolve = o.getModuleIdentifier().resolve();
                if (resolve != null) {
                    processor.execute(resolve, state.put(ACTUAL_NAME, o.getModuleIdentifier().getText()));
                    if (!processModuleLevelEntities((ResFile) resolve, processor, state, forSuperModules)) return false;
                }
            }
        }
        return true;
    }

    protected static boolean processSuperModulesUsesImports(@NotNull ResFile file,
                                                            @NotNull ResScopeProcessor processor,
                                                            @NotNull ResolveState state) {
        ResModuleDecl module = file.getEnclosedModule();
        if (module == null) return true;

        List<ResModuleIdentifierSpec> sourceIdentifierSpecs = file.getModuleIdentifierSpecs();
        for (ResReferenceExp moduleRef : module.getModuleHeaderReferences()) {
            PsiElement resolvedFile = moduleRef.resolve(); //resolve the header reference from my own uses list.
            if (resolvedFile == null || !(resolvedFile instanceof ResFile)) continue;
            processUsesImports((ResFile) resolvedFile, processor, state, true);
        }
        return true;
    }

    private static boolean isSearchableUsesModule(@NotNull ResFile e) {
        if (e.getEnclosedModule() == null) return true; //its ok if we're null
        ResModuleDecl m = e.getEnclosedModule();
        return m instanceof ResFacilityModuleDecl ||
                m instanceof ResPrecisModuleDecl ||
                m instanceof ResConceptModuleDecl;
    }

    static boolean processModuleLevelEntities(@NotNull ResFile file,
                                              @NotNull ResScopeProcessor processor,
                                              @NotNull ResolveState state,
                                              boolean localProcessing) {
        return processModuleLevelEntities(file, processor, state, localProcessing, false);
    }

    static boolean processModuleLevelEntities(@NotNull ResFile file,
                                              @NotNull ResScopeProcessor processor,
                                              @NotNull ResolveState state,
                                              boolean localProcessing,
                                              boolean fromFacilities) {
        if (file.getEnclosedModule() == null) return true;
        return processModuleLevelEntities(file.getEnclosedModule(), processor, state, localProcessing, fromFacilities);
    }

    //TODO: Going to want to do some filtering here in the case where a uses clause initiates the processing
    // ... in which case if the module is a concept, only return the set of math
    //{defns}, if its facility -- then search {opproc decls} U {type reprs} U {math defns}.
    static boolean processModuleLevelEntities(@NotNull ResModuleDecl module,
                                              @NotNull ResScopeProcessor processor,
                                              @NotNull ResolveState state,
                                              boolean localProcessing,
                                              boolean fromFacility) {
        if (!processNamedElements(processor, state, module.getOperationLikeThings(), localProcessing, fromFacility)) return false;
        if (!processNamedElements(processor, state, module.getFacilities(), localProcessing, fromFacility)) return false;
        if (!processNamedElements(processor, state, module.getTypes(), localProcessing, fromFacility)) return false;

        //module parameter-like-things
        if (!processNamedElements(processor, state, module.getGenericTypeParams(), localProcessing, fromFacility)) return false;
        if (!processNamedElements(processor, state, module.getConstantParamDefs(), localProcessing, fromFacility)) return false;

        if (!processNamedElements(processor, state, module.getMathDefnSigs(), localProcessing, fromFacility)) return false;
        return true;
    }

    /** processing parameters of the definition we happen to be within */
    private static boolean processDefinitionParams(@NotNull ResScopeProcessorBase processor,
                                                   @NotNull ResMathDefnDecl o) {
        List<ResMathDefnSig> sigs = o.getSignatures();
        if (sigs.size() == 1) {
            ResMathDefnSig sig = o.getSignatures().get(0);
            if (!processDefinitionParams(processor, sig.getParameters())) return false;
        } //size > 1 ? then we're categorical; size == 0, we're null
        return true;
    }

    private static boolean processDefinitionParams(@NotNull ResScopeProcessorBase processor,
                                                   @NotNull List<ResMathVarDeclGroup> parameters) {
        for (ResMathVarDeclGroup declaration : parameters) {
            if (!processNamedElements(processor, ResolveState.initial(), declaration.getMathVarDefList(), true)) return false;
        }
        return true;
    }

    private static boolean processProgParamDecls(@NotNull ResScopeProcessorBase processor,
                                                 @NotNull List<ResParamDecl> parameters) {
        for (ResParamDecl declaration : parameters) {
            if (!processNamedElements(processor, ResolveState.initial(), declaration.getParamDefList(), true)) return false;
        }
        return true;
    }

    //move some of this logic into ResModuleDecl (the abstract base class or whatever)
    private static boolean processModuleParams(@NotNull ResScopeProcessorBase processor, @NotNull ResModuleDecl o) {
        ResModuleParameters paramNode = o.getModuleParameters();
        List<ResTypeParamDecl> typeParamDecls = new ArrayList<>();
        List<ResParamDecl> constantParamDeclGrps = new ArrayList<>();
        List<ResMathDefnDecl> definitionParams = new ArrayList<>();
        List<ResMathDefnSig> defnSigs = new ArrayList<>();

        if (paramNode instanceof ResSpecModuleParameters) {
            typeParamDecls.addAll(((ResSpecModuleParameters) paramNode).getTypeParamDeclList());
            constantParamDeclGrps.addAll(((ResSpecModuleParameters) paramNode).getParamDeclList());
            definitionParams.addAll(((ResSpecModuleParameters) paramNode).getMathStandardDefnDeclList());
        }
        if (paramNode instanceof ResImplModuleParameters) {
            //definitionParams.addAll(((ResImplModuleParameters) paramNode).g
        }
        for (ResMathDefnDecl d : definitionParams) {
            defnSigs.addAll(d.getSignatures());
        }
        //TODO: else if (paramNode instanceof ResImplModuleParameters) ..
        processProgParamDecls(processor, constantParamDeclGrps);
        processNamedElements(processor, ResolveState.initial(), typeParamDecls, true);
        processNamedElements(processor, ResolveState.initial(), defnSigs, true);
        return true;
    }

    static boolean processNamedElements(@NotNull PsiScopeProcessor processor,
                                        @NotNull ResolveState state,
                                        @NotNull Collection<? extends ResNamedElement> elements,
                                        boolean localResolve) {
        return processNamedElements(processor, state, elements, localResolve, false);
    }

    static boolean processNamedElements(@NotNull PsiScopeProcessor processor,
                                        @NotNull ResolveState state,
                                        @NotNull Collection<? extends ResNamedElement> elements,
                                        boolean localResolve,
                                        boolean facilityResolve) {
        for (ResNamedElement definition : elements) {
            //if (!definition.isValid() || checkContainingFile && !allowed(definition.getContainingFile(), contextFile)) continue;
            if ((localResolve || definition.isUsesClauseVisible() || facilityResolve) &&
                    !processor.execute(definition, state)) return false;
        }
        return true;
    }

    @NotNull
    private ResVarReference.ResVarProcessor createDelegate(@NotNull ResScopeProcessor processor) {
        return new ResVarReference.ResVarProcessor(getIdentifier(), myElement, processor.isCompletion(), true) {
            @Override
            protected boolean crossOff(@NotNull PsiElement e) {
                return e instanceof ResFieldDef || super.crossOff(e);
            }
        };
    }

    private String getName() {
        return myElement.getIdentifier().getText();
    }

    @Nullable
    private static PsiFile getContextFile(@NotNull ResolveState state) {
        SmartPsiElementPointer<ResReferenceExpBase> context = state.get(CONTEXT);
        return context != null ? context.getContainingFile() : null;
    }
}