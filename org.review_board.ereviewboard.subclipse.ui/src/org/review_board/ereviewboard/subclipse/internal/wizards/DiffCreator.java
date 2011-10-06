package org.review_board.ereviewboard.subclipse.internal.wizards;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.tigris.subversion.svnclientadapter.ISVNClientAdapter;
import org.tigris.subversion.svnclientadapter.SVNClientException;

/**
 * The <tt>DiffCreator</tt> creates ReviewBoard-compatible diffs
 * 
 * <p>Once specific problem with svn diff is that moved files have an incorrect header.</p>
 * 
 * @see <a href="https://github.com/reviewboard/rbtools/blob/release-0.3.4/rbtools/postreview.py#L1731">post-review handling of svn renames</a>
 * @author Robert Munteanu
 */
public class DiffCreator {

    private static final String INDEX_MARKER = "Index:";

    public byte[] createDiff(Set<ChangedFile> selectedFiles, File rootLocation, ISVNClientAdapter svnClient) throws IOException, SVNClientException {

        File tmpFile = null;

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            tmpFile = File.createTempFile("ereviewboard", "diff");

            List<File> changes = new ArrayList<File>(selectedFiles.size());
            Map<String, String> copies = new HashMap<String, String>();
            for (ChangedFile changedFile : selectedFiles) {
                if (changedFile.getCopiedFromPathRelativeToProject() != null)
                    copies.put(changedFile.getPathRelativeToProject(), changedFile.getCopiedFromPathRelativeToProject());
                changes.add(changedFile.getFile());
            }

            svnClient.createPatch(changes.toArray(new File[changes.size()]), rootLocation, tmpFile, false);

            @SuppressWarnings("unchecked")
            List<String> patchLines = FileUtils.readLines(tmpFile);
            int replaceIndex = -1;
            String replaceFrom = null;
            String replaceTo = null;

            for (int i = 0; i < patchLines.size(); i++) {

                String line = patchLines.get(i);

                if ( line.toString().startsWith(INDEX_MARKER) ) {
                    String file = line.substring(INDEX_MARKER.length()).trim();

                    String copiedTo = copies.get(file);
                    if (copiedTo != null) {
                        System.out.println("File " + file + " is copied to " + copiedTo + " .");
                        replaceIndex = i + 2;
                        replaceFrom = file;
                        replaceTo = copiedTo;
                    }
                } else if (i == replaceIndex) {
                    line = line.replace(replaceFrom, replaceTo);
                }

                outputStream.write(line.getBytes());
                outputStream.write('\n');
            }

            return outputStream.toByteArray();
        } finally {
            FileUtils.deleteQuietly(tmpFile);
        }
    }
}
