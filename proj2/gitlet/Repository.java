package gitlet;

import java.io.File;
import java.io.IOException;

import java.util.*;

import static gitlet.Utils.*;



/** Represents a gitlet repository.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author TODO
 */
public class Repository {
    /**
     * TODO: add instance variables here.
     * <p>
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */
    public static StagingArea stage = new StagingArea();

    /**
     * The current working directory.
     */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /**
     * The .gitlet directory.
     */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /**
     * The .blob directory.
     */
    public static final File BLOB_DIR = join(GITLET_DIR, "blobs");

    public static final File BRANCH_DIR = join(GITLET_DIR, "branches");

    public static final File COMMIT_DIR = join(GITLET_DIR, "commits");

    public static final File STAGE_DIR = join(GITLET_DIR, "staging");

    public static final File GLOBAL_LOG_DIR = join(GITLET_DIR, "global_log");

    public static StagingArea getStagingArea() {
        File pathToStage = join(STAGE_DIR, "stage.txt");
        if (!pathToStage.exists()) {
            return new StagingArea();
        } else {
            return readObject(pathToStage, StagingArea.class);
        }
    }


    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
        } else {
            GITLET_DIR.mkdir();
            BLOB_DIR.mkdir();
            BRANCH_DIR.mkdir();
            COMMIT_DIR.mkdir();
            GLOBAL_LOG_DIR.mkdir();
            STAGE_DIR.mkdir();

            // init first commit and save it in /commits
            Commit initCommit = new Commit();
            File initCommitFile = join(COMMIT_DIR, initCommit.getOwnRefHash() + ".txt");
            writeObject(initCommitFile, initCommit);


            // make a master branch file in /branches
            File pathToMaster = join(BRANCH_DIR, "master.txt");
            writeContents(pathToMaster, initCommit.getOwnRefHash());


            // write HEAD file in /branches
            File pathToHead = join(BRANCH_DIR, "HEAD.txt");
            writeContents(pathToHead, "master.txt");
            //init stageArea
            stage = getStagingArea();
        }

    }


    public static void checkInitialized() {
        if (!GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }

    public static void add(String fileName) throws IOException {
        File filename = getFileFromCWD(fileName);
        stage = getStagingArea();
        File toStagePath = join(STAGE_DIR, "stage.txt");
        if (!filename.exists()) {
            System.out.println("File does not exist.");
            System.exit(0);
        }
        String contents = readContentsAsString(filename);
        byte[] fileContents = readContents(filename);
        String blobHash = sha1(fileContents, fileName);
        //save blob if it's new
        if (!join(BLOB_DIR, blobHash + ".txt").exists()) {
            writeContents(join(BLOB_DIR, blobHash + ".txt"), contents);
        }
        Commit curr = getHeadCommit();
        // remove and then add again
        if (stage.getRemovedFiles().contains(fileName)) {
            stage.getRemovedFiles().remove(fileName);
        }
        // new file or first time modified
        else if (!stage.getAddedFiles().containsKey(fileName) && !curr.getBlobs().containsKey(fileName)) {
            stage.add(fileName, blobHash);
        }
        // modified file
        else if (curr.getBlobs().containsKey(fileName) && !curr.getBlobs().get(fileName).equals(blobHash)) {
            stage.add(fileName, blobHash);
        }
        writeContents(join(BLOB_DIR, blobHash + ".txt"), contents);
        writeObject(toStagePath, stage);

    }

    public static HashMap<String, String> getNewBlob(Commit c, StagingArea stage) {
        HashMap<String, String> originalBlob = c.getBlobs();
        HashMap<String, String> newBlob = new HashMap<>(originalBlob);

        for (Map.Entry<String, String> entry : stage.getAddedFiles().entrySet()) {
            newBlob.put(entry.getKey(), entry.getValue());
        }
        for (String removedFile : stage.getRemovedFiles()) {
            newBlob.remove(removedFile);
        }
        return newBlob;
    }

    public static void commit(String message) {
        //change could be adding or deleting, so there are addedFile and deletedFile
        stage = getStagingArea();
        if (stage.getAddedFiles().isEmpty()&& stage.getRemovedFiles().isEmpty()) {
            System.out.println("No changes added to the commit.");
            return;
        } else if (message.equals("")) {
            System.out.println("Please enter a commit message.");
            System.exit(0);
        }
        //parentID
        String currentBranch = readContentsAsString(join(BRANCH_DIR, "HEAD.txt"));
        String parentID = readContentsAsString(join(BRANCH_DIR, currentBranch));
        //copiedBlob
        Commit prevCommit = readObject(join(COMMIT_DIR, parentID + ".txt"), Commit.class);
        HashMap<String, String> copiedBlob = getNewBlob(prevCommit, stage);
        //create and save new commit
        Commit newCommit = new Commit(message, copiedBlob, parentID);
        File newCommitFile = join(COMMIT_DIR, newCommit.getOwnRefHash() + ".txt");
        writeObject(newCommitFile, newCommit);
        //update head pointer
        writeContents(join(BRANCH_DIR, currentBranch), newCommit.getOwnRefHash());


        //clear stagingArea
        stage.clear();
        writeObject(join(STAGE_DIR, "stage.txt"), stage);
    }

    public static void rm(String fileName) {
        Commit curr = getHeadCommit();
        stage = getStagingArea();
        boolean isStaged = stage.getAddedFiles().containsKey(fileName);
        boolean isTracked = false;
        ArrayList<String> committedFiles = new ArrayList<>(curr.getBlobs().keySet());
        for (String f : committedFiles) {
            if (f.equals(fileName)) {
                isTracked = true;
            }
        }
        if (isTracked) {
            stage.addToRemovedFiles(fileName);
            restrictedDelete(fileName);
            if (isStaged) {
                stage.getAddedFiles().remove(fileName);
            }
            writeObject(join(STAGE_DIR, "stage.txt"), stage);
        } else if (isStaged) {
            stage.getAddedFiles().remove(fileName);
            writeObject(join(STAGE_DIR, "stage.txt"), stage);
        } else {
            System.out.println("No reason to remove the file.");
        }

    }

    public static void log() {
        Commit curr = getHeadCommit();
        //TODO: consider merge situation
        while (curr != null) {
            System.out.println("===");
            System.out.println("commit " + curr.getOwnRefHash());
            System.out.println("Date: " + curr.getTimestamp());
            System.out.println(curr.getMessage() + "\n");
            if (curr.getParentRefHash() != null) {
                curr = readObject(join(COMMIT_DIR, curr.getParentRefHash() + ".txt"), Commit.class);
            } else {
                curr = null;
            }
        }
    }

    public static void global_log() {
        for (File commitFile : Objects.requireNonNull(COMMIT_DIR.listFiles())) {
            Commit commit = readObject(commitFile, Commit.class);
            System.out.println("===");
            System.out.println("commit " + commit.getOwnRefHash());
            System.out.println("Date: " + commit.getTimestamp());
            System.out.println(commit.getMessage() + "\n");
        }
    }

    public static void find(String message) {
        ArrayList<String> matchingCommits = new ArrayList<>();
        for (File commitFile : Objects.requireNonNull(COMMIT_DIR.listFiles())) {
            Commit commit = readObject(commitFile, Commit.class);
            if (commit.getMessage().equals(message)) {
                matchingCommits.add(commit.getOwnRefHash());
            }
        }
        if (matchingCommits.size() == 0) {
            System.out.println("Found no commit with that message");
        } else {
            for (String str : matchingCommits) {
                System.out.println(str);
            }
        }
    }

    public static void status() {
        System.out.println("=== Branches ===");
        String headBranchName = readContentsAsString(join(BRANCH_DIR, "HEAD.txt"));
        System.out.println("*" + removeTXT(headBranchName));
        File[] files = BRANCH_DIR.listFiles();
        Arrays.sort(files);
        for (File f : files) {
            if (!f.getName().equals(headBranchName) && !f.getName().equals("HEAD.txt")) {
                System.out.println(removeTXT(f.getName()));
            }
        }
        System.out.println();
        stage = getStagingArea();
        ArrayList<String> stagedFiles = new ArrayList<>();
        for (Map.Entry<String, String> entry : stage.getAddedFiles().entrySet()) {
            stagedFiles.add(entry.getKey());
        }
        Collections.sort(stagedFiles);
        System.out.println("=== Staged Files ===");
        for (String staged : stagedFiles) {
            System.out.println(staged);
        }
        System.out.println();
        ArrayList<String> removedFiles = new ArrayList<>(stage.getRemovedFiles());
        Collections.sort(removedFiles);
        System.out.println("=== Removed Files ===");
        for (String removed : removedFiles) {
            System.out.println(removed);
        }
        System.out.println();
        System.out.println("=== Modifications Not Staged For Commit ===");
        HashMap<File, String> modifiedButNotStaged = modifiedButNotStaged();
        Collections.sort(new ArrayList<>(modifiedButNotStaged.keySet()));
//        for (Map.Entry<File, String> entry : modifiedButNotStaged.entrySet()) {
//            System.out.println(entry.getKey().getName() + " (" + entry.getValue() + ")");
//        }
        System.out.println();
        System.out.println("=== Untracked Files ===");
        ArrayList<File> untrackedFiles = untrackedFile();
        Collections.sort(untrackedFiles);
        for (File f : untrackedFiles) {
            System.out.println(f.getName());
        }
        System.out.println();

    }

    public static void checkout(String... args) {
        /** case 1: -- [filename]
         * Failure case:File does not exist in that commit.
         */
        stage = getStagingArea();
        if (args.length == 3 && args[1].equals("--")) {
            Commit currCommit = getHeadCommit();
            File file = getFileFromCWD(args[2]);
            revertFile(file, currCommit);
        } else if (args.length == 4 && args[2].equals("--")) {
            //case 2: [commit id] -- [filename]
            File commitFile;
            if (args[1].length() < UID_LENGTH) {
                commitFile = getFileShorter(args[1], COMMIT_DIR.listFiles());
            } else {
                commitFile = getFile(args[1] + ".txt", COMMIT_DIR.listFiles());
            }
            if (commitFile == null) {
                System.out.println("No commit with that id exists.");
                return;
            }
            Commit commit = readObject(commitFile, Commit.class);
            File file = getFileFromCWD(args[3]);
            revertFile(file, commit);
        } else if (args.length == 2) {
            // case 3: [branchName]
            String branchName = args[1];
            File branchFile = getFile(branchName + ".txt", BRANCH_DIR.listFiles());
            if (branchFile == null) {
                System.out.println("No such branch exists");
                return;
            } else if (readContentsAsString(join(BRANCH_DIR, "HEAD.txt")).equals(branchFile.getName())) {
                System.out.println("No need to checkout the current branch.");
                return;
            }
            //get all files at the head of the given branch
            String commitID = readContentsAsString(branchFile);
            File commitFile = join(COMMIT_DIR, commitID+ ".txt");
            Commit newCommit = readObject(commitFile, Commit.class);  // desired commit
            Commit currCommit = getHeadCommit();
            //and puts them in the working directory, overwriting previous version
            untrackedFileError(newCommit, currCommit);
            for (String filePath : newCommit.getBlobs().keySet()) {
                revertFile(getFileFromCWD(filePath), newCommit);
            }
            for (String pathToID : currCommit.getBlobs().keySet()) {
                if (!newCommit.getBlobs().containsKey(pathToID)) {
                    restrictedDelete(pathToID);
                }
            }
            // clear stagingArea
            stage.clear();
            writeObject(join(STAGE_DIR, "stage.txt"), stage);
            writeContents(join(BRANCH_DIR, "HEAD.txt"), branchName + ".txt");
        } else {
            System.out.println("Incorrect operands.");
            System.exit(0);
        }
    }

    public static void branch(String branchName) {
        File branchFile = join(BRANCH_DIR, branchName + ".txt");
        if (branchFile.exists()) {
            System.out.println("A branch with that name already exists.");
            return;
        }
        String sha1 = getHeadCommit().getOwnRefHash();
        writeContents(branchFile, sha1);

    }

    public static void rm_Branch(String branchName) {
        File branchFile = getFile(branchName + ".txt",
                BRANCH_DIR.listFiles());
        if (branchFile == null) {
            System.out.println("A branch with that name does not exist.");
            return;
        } else if (getHeadBranchName().getName().equals(branchFile.getName())) {
            System.out.println("Cannot remove the current branch.");
            return;
        } else {
            branchFile.delete();
        }

    }

    public static void reset(String commitID) {
        //check if reset commit exists
        if (getFile(commitID + ".txt", COMMIT_DIR.listFiles()) == null) {
            System.out.println("No commit with that id exists.");
            return;
        } else if (getHeadCommit().getOwnRefHash().equals(commitID)) {
            System.out.println("No need to reset to the current commit.");
            return;
        }
        String pastPathway = readContentsAsString(join(BRANCH_DIR, "HEAD.txt"));
        String branchName = sha1("branchToBeUsedForCheckOut");

        // create a new branch and head pointer points to same commit as current commit

        branch(branchName);

        // move HEAD to checkout branch/commit

        writeContents(join(BRANCH_DIR, branchName + ".txt"), commitID); //write reset commit id to new branch

        String[] input = {"checkout", branchName};

        checkout(input);  //check out to new branch


        // read stored files in current commit
        // delete previous stored files (original commit)
        writeContents(join(BRANCH_DIR, "HEAD.txt"), pastPathway);
        writeContents(getHeadBranchName(), commitID);
        rm_Branch(branchName);



    }


    /**
     * Merges files from the given branch into the current branch.
     *
     * @param branchName
     * @return
     */

    public static void merge(String branchName) {
        Commit currentCommit = getHeadCommit();
        stage = getStagingArea();
        //get branchFile from given branch
        File branchFile = getFile(branchName + ".txt", BRANCH_DIR.listFiles());
        if (branchFile == null) {
            System.out.println("A branch with that name doesn't exist");
            return;
        } else if (readContentsAsString(branchFile).equals(currentCommit.getOwnRefHash())) {
            System.out.println("Cannot merge a branch with itself.");
            return;
        }
        if (!stage.getAddedFiles().isEmpty() || !stage.getRemovedFiles().isEmpty()) {
            System.out.println("You have uncommitted changes.");
            return;
        }
        //get commit file from given branch
        String commitID = readContentsAsString(branchFile);
        Commit branchCommit = readObject(join(COMMIT_DIR, commitID + ".txt"), Commit.class);
        Commit splitPoint = findSplitPoint(currentCommit, branchCommit);
        if (splitPoint == null) {
            System.out.println("Unknown error, split commit not found");
            return;
        } else if (splitPoint.getOwnRefHash().equals(commitID)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        } else if (splitPoint.getOwnRefHash().equals(currentCommit.getOwnRefHash())) {
            String[] input = {"checkout", branchName};
            checkout(input);
            System.out.println("Current branch fast-forwarded.");
            return;
        }
        untrackedFileError(branchCommit, currentCommit);
        checkCommitTobeModified(branchCommit, currentCommit, splitPoint);
        for (String filePath : currentCommit.getBlobs().keySet()) {
            if (!branchCommit.getBlobs().containsKey(filePath) && !splitPoint.getBlobs().containsKey(filePath)) {
                stage.add(filePath, readContentsAsString(new File(filePath)));
                writeObject(join(STAGE_DIR, "stage.txt"), stage);
            }
        }
        checkSplitCommit(branchCommit, currentCommit, splitPoint);
        boolean conflict = checkMergeConflict(branchCommit, currentCommit);
        commit("Merged " +branchName + " into " + removeTXT(getHeadBranchName().getName()) + ".");
        if (conflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /**
     * A helper method to find the split point between the
     * two branches by iterating through the parents of the
     * commits.
     */

    public static Commit findSplitPoint(Commit currentCommit, Commit branchCommit) {
        HashSet<String> visited = new HashSet<>();
        //add all the parents of the current commit to the set
        String currentCommitID = currentCommit.getOwnRefHash();
        visited.add(currentCommitID);
        Queue<Commit> queue = new LinkedList<>();
        queue.add(currentCommit);
        while (!queue.isEmpty()){
            for (int i = 0; i < queue.size(); i++) {
                Commit curr = queue.poll();
                if (curr.getParentRefHash() != null && !visited.contains(curr.getParentRefHash())) {
                    visited.add(curr.getParentRefHash());
                    Commit parent = readObject(join(COMMIT_DIR, curr.getParentRefHash() + ".txt"), Commit.class);
                    queue.add(parent);
                }
            }
        }
        //check if the branch commit is in the set
        queue.add(branchCommit);
        while (!queue.isEmpty()) {
            for (int i = 0; i < queue.size(); i++) {
                Commit curr = queue.poll();
                if (visited.contains(curr.getOwnRefHash())) {
                    return curr;
                }
                if (curr.getParentRefHash() != null) {
                    Commit parent = readObject(join(COMMIT_DIR, curr.getParentRefHash() + ".txt"), Commit.class);
                    queue.add(parent);
                }
            }
        }
        return null;
    }

//    public static void writeConflict(String fileName,Commit headCommit,Commit branchCommit) {
//        String headContent = readContentsAsString(join(headCommit.getBlobs().get(fileName)+".txt"));
//        String branchContent = readContentsAsString(join(branchCommit.getBlobs().get(fileName)+".txt"));
//        String conflictContent = "<<<<<<< HEAD\n" + headContent + "=======\n" + branchContent + ">>>>>>>\n";
//        writeContents(new File(fileName), conflictContent);
//    }


    // case 3: modified in other and head; in same way or different way
    public static boolean checkMergeConflict(Commit commit, Commit currCommit) {
        boolean conflict = false;
        stage = getStagingArea();
        for (String filePath : commit.getBlobs().keySet()) {
            if (currCommit.getBlobs().containsKey(filePath) && !currCommit.getBlobs().get(filePath).equals(commit.getBlobs().get(filePath))) {
                String contents = readContentsAsString(join(BLOB_DIR, commit.getBlobs().get(filePath) + ".txt"));
                String currContents = readContentsAsString(join(BLOB_DIR, currCommit.getBlobs().get(filePath) + ".txt"));

                String replacedContent = "<<<<<<< HEAD\n" + currContents +
                        "=======\n" + contents + ">>>>>>>\n";
                File conflictFile = getFileFromCWD(filePath);
                writeStringToFile(replacedContent, conflictFile.getName(), false);
                stage.add(conflictFile.getName(), sha1(replacedContent, conflictFile.getName()));
                writeObject(join(STAGE_DIR, "stage.txt"), stage);
                conflict = true;
            }
        }
        return conflict;
    }

    /**
     * A helper method to check whether a file has been modified
     * between two commit.
     **/

    public static boolean checkModifiedFile(String filePath, String currContentID, Commit start, Commit end) {
        Commit curr = start;
        while (!curr.getOwnRefHash().equals(end.getOwnRefHash())&& curr.getParentRefHash()!=null) {
            if (curr.getBlobs().containsKey(filePath) && !curr.getBlobs().get(filePath).equals(currContentID)) {
                return true;
            }
            curr = curr.getParentRefHash()!=null?readObject(getFile(curr.getParentRefHash() + ".txt", COMMIT_DIR.listFiles()), Commit.class):null;
        }
        return false;
    }

    /**
     * A helper method that iterates through the commit
     * and checks if files need to be changed or updated.
     * case 5,1
     **/

    public static void checkCommitTobeModified(Commit commit, Commit currCommit, Commit splitCommit) {
        stage = getStagingArea();
        for (String filePath : commit.getBlobs().keySet()) {
            File f = getFileFromCWD(filePath);
            boolean cond1 = !currCommit.getBlobs().containsKey(filePath)
                    && !splitCommit.getBlobs().containsKey(filePath);
            boolean cond2 = splitCommit.getBlobs().containsKey(filePath)
                    && !splitCommit.getBlobs().get(filePath).equals(commit.getBlobs().get(filePath))
                    && !checkModifiedFile(filePath, splitCommit.getBlobs().get(filePath), currCommit, splitCommit);

            if (cond1 || cond2) {
                revertFile(f, commit);
                stage.add(filePath, commit.getBlobs().get(filePath));
                writeObject(join(STAGE_DIR, "stage.txt"), stage);
                //?how to distinguish removed or added files?
            }
        }
    }


    /**
     * A helper method to check on the files
     * present / modified in the split commit
     * in the split commit.
     * case 6/7
     **/

    public static void checkSplitCommit(Commit commit, Commit currCommit, Commit splitCommit) {
        stage = getStagingArea();
        for (String filePath : splitCommit.getBlobs().keySet()) {
            if (!commit.getBlobs().containsKey(filePath) && !checkModifiedFile(filePath, splitCommit.getBlobs().get(filePath), currCommit, splitCommit)) {
                restrictedDelete(filePath);
                Commit curr = getHeadCommit();
                if (curr.getBlobs().containsKey(filePath)) {
                    stage.addToRemovedFiles(filePath);
                    curr.getBlobs().remove(filePath);
                }
            } else if (!currCommit.getBlobs().containsKey(filePath) && !checkModifiedFile(filePath, splitCommit.getBlobs().get(filePath), commit, splitCommit)) {
                stage.addToRemovedFiles(filePath);
                restrictedDelete(filePath);
            }
        }
    }


    public static File getFileFromCWD(String file) {
        return join(CWD, file);
    }

    public static String removeTXT(String fileName) {
        return fileName.substring(0, fileName.length() - 4);
    }

    public static File getFile(String name, File[] files) {
        if (name.isEmpty()) {
            return null;
        }
        for (File f : files) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    public static File getFileShorter(String name, File[] files) {
        if (name.isEmpty()) {
            return null;
        }
        for (File f : files) {
            if (f.getName().substring(0, name.length()).equals(name)) {
                return f;
            }
        }
        return null;
    }

    public static void untrackedFileError(Commit commit, Commit headCommit) {
        for (String filePath : commit.getBlobs().keySet()) {
            if (new File(filePath).exists()) {
                String currentContents = sha1(readContentsAsString(new File(filePath)), filePath);
                if (!headCommit.getBlobs().containsKey(filePath) && !currentContents.equals(commit.getBlobs().get(filePath))) {
                    System.out.println("There is an untracked file in the way; " +
                            "delete it, or add and commit it first.");
                    return;
                }
            }
        }
    }


    public static void revertFile(File f, Commit commit) {
        if (!commit.getBlobs().containsKey(f.getName())) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        //same file name, different sha1
        //get sha1 from file in desired commit
        String contentID = commit.getBlobs().get(f.getName());

        //find out the file with desired sha1
        File file = getFile(contentID + ".txt", BLOB_DIR.listFiles());
        if (file == null) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        //if in the desired commit, the file is deleted
        if (contentID == null) {
            restrictedDelete(f.getName());
            return;
        }

        String contents = readContentsAsString(file);
        writeContents(f, contents);

    }


    public static File getHeadBranchName() {
        String headBranchName = readContentsAsString(join(BRANCH_DIR, "HEAD.txt"));
        return join(BRANCH_DIR, headBranchName);
    }

    public static Commit getHeadCommit() {
        File headBranch = getHeadBranchName();
        String commitID = readContentsAsString(headBranch);
        File commitFile = join(COMMIT_DIR, commitID + ".txt");
        if (getFile(commitID + ".txt", COMMIT_DIR.listFiles()) == null) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        return readObject(commitFile, Commit.class);
    }

    public static ArrayList<File> untrackedFile() {
        Commit headCommit = getHeadCommit();
        stage = getStagingArea();
        ArrayList<File> untrackedFiles = new ArrayList<>();
        for (File f : CWD.listFiles()) {
            if (!f.isDirectory()) {
                if (!headCommit.getBlobs().containsKey(f.getName()) && !stage.getAddedFiles().containsKey(f.getName())) {
                    untrackedFiles.add(f);
                } else if (stage.getRemovedFiles().contains(f.getName())&&!headCommit.getBlobs().containsKey(f.getName())){
                    untrackedFiles.add(f);
                }
            }
        }
        return untrackedFiles;
    }

    /**
     * A file in the working directory is “modified but not staged” if it is
     * •	Tracked in the current commit, changed in the working directory, but not staged; (modified)
     * •	Staged for addition, but with different contents than in the working directory;(modified)
     * •	Staged for addition, but deleted in the working directory;(deleted)
     * •	Not staged for removal, but tracked in the current commit and deleted from the working directory.(deleted)
     *
     * @return HashMap<File, String>
     */

    public static HashMap<File, String> modifiedButNotStaged() {
        Commit headCommit = getHeadCommit();
        stage = getStagingArea();
        HashMap<File, String> modifiedButNotStaged = new HashMap<>();

        for (String fileName : stage.getAddedFiles().keySet()) {
            if (getFileFromCWD(fileName).exists()
                    && !stage.getAddedFiles().get(fileName).equals(sha1(readContentsAsString(getFileFromCWD(fileName)), fileName))) {
                modifiedButNotStaged.put(getFileFromCWD(fileName), "modified");
            } else if (!getFileFromCWD(fileName).exists() && getHeadCommit().getBlobs().containsKey(fileName)) {
                modifiedButNotStaged.put(new File(fileName), "deleted");
            }
        }

        for (String fileName : headCommit.getBlobs().keySet()) {
            if (getFileFromCWD(fileName).exists()) {
                if (!headCommit.getBlobs().get(fileName).equals(sha1(readContentsAsString(getFileFromCWD(fileName)), fileName))
                        && !stage.getAddedFiles().containsKey(fileName)
                        && !stage.getRemovedFiles().contains(fileName)) {
                    modifiedButNotStaged.put(getFileFromCWD(fileName), "modified");
                }
            } else if (!stage.getRemovedFiles().contains(fileName) && !getFileFromCWD(fileName).exists()) {
                modifiedButNotStaged.put(getFileFromCWD(fileName), "deleted");
            }
        }

        return modifiedButNotStaged;
    }
}
