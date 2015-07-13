package edu.clemson.resolve.plugin.runconfig;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RESOLVERunUtil {

    public static void installFileChooser(@NotNull Project project,
              @NotNull ComponentWithBrowseButton field, boolean directory) {
        installFileChooser(project, field, directory, null);
    }

    public static void installFileChooser(@NotNull Project project,
                              @NotNull ComponentWithBrowseButton field,
                              boolean directory,
                              @Nullable Condition<VirtualFile> fileFilter) {
        FileChooserDescriptor chooseDirectoryDescriptor = directory
                ? FileChooserDescriptorFactory.createSingleFolderDescriptor()
                : FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
        chooseDirectoryDescriptor.setRoots(project.getBaseDir());
        chooseDirectoryDescriptor.setShowFileSystemRoots(false);
        chooseDirectoryDescriptor.withFileFilter(fileFilter);

        if (field instanceof TextFieldWithBrowseButton) {
            ((TextFieldWithBrowseButton)field).addBrowseFolderListener(
                    new TextBrowseFolderListener(chooseDirectoryDescriptor,
                            project));
        }
        else {
            //noinspection unchecked
            field.addBrowseFolderListener(project,
                    new ComponentWithBrowseButton.BrowseFolderActionListener(
                            null, null, field, project,
                    chooseDirectoryDescriptor,
                    TextComponentAccessor.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT));
        }
    }
}
