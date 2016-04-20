package edu.clemson.resolve.jetbrains.project;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

public class RESOLVELibrariesService<T extends RESOLVELibrariesState>
        extends
            SimpleModificationTracker implements PersistentStateComponent<T> {

    public static final Topic<LibrariesListener> LIBRARIES_TOPIC =
            new Topic<LibrariesListener>("libraries changes", LibrariesListener.class);

    protected final T state = createState();

    @NotNull @Override public T getState() {
        return state;
    }

    @Override public void loadState(T state) {
        XmlSerializerUtil.copyBean(state, state);
    }

    @NotNull protected T createState() {
        //noinspection unchecked
        return (T)new RESOLVELibrariesState();
    }

    @NotNull public static Collection<? extends VirtualFile> getUserDefinedLibraries(
            @NotNull Module module) {
        Set<VirtualFile> result = ContainerUtil.newLinkedHashSet();
        result.addAll(resolveLangRootsFromUrls(RESOLVEModuleLibrariesService
                .getInstance(module).getLibraryRootUrls()));
        result.addAll(getUserDefinedLibraries(module.getProject()));
        return result;
    }

    @NotNull public static Collection<? extends VirtualFile> getUserDefinedLibraries(
            @NotNull Project project) {
        Set<VirtualFile> result = ContainerUtil.newLinkedHashSet();
        result.addAll(resolveLangRootsFromUrls(RESOLVEProjectLibrariesService
                .getInstance(project).getLibraryRootUrls()));
        result.addAll(getUserDefinedLibraries());
        return result;
    }

    @NotNull public static Collection<? extends VirtualFile> getUserDefinedLibraries() {
        return resolveLangRootsFromUrls(RESOLVEApplicationLibrariesService
                .getInstance().getLibraryRootUrls());
    }


    @NotNull public Collection<String> getLibraryRootUrls() {
        return state.getUrls();
    }

    @NotNull private static Collection<? extends VirtualFile> resolveLangRootsFromUrls(
            @NotNull Collection<String> urls) {
        return ContainerUtil.mapNotNull(urls,
                new Function<String, VirtualFile>() {

            @Override public VirtualFile fun(String url) {
                return VirtualFileManager.getInstance().findFileByUrl(url);
            }
        });
    }

    public interface LibrariesListener {
        void librariesChanged(@NotNull Collection<String> newRootUrls);
    }
}
