package hk.ust.comp3021;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import hk.ust.comp3021.action.Action;
import hk.ust.comp3021.action.AddCommentAction;
import hk.ust.comp3021.action.AddLabelAction;
import hk.ust.comp3021.action.DownloadPaperAction;
import hk.ust.comp3021.action.LabelAction;
import hk.ust.comp3021.action.LabelActionList;
import hk.ust.comp3021.action.ParallelImportAction;
import hk.ust.comp3021.action.QueryAction;
import hk.ust.comp3021.action.SearchMultipleKeywordsAction;
import hk.ust.comp3021.action.SearchPaperAction;
import hk.ust.comp3021.action.SearchPaperAction.SearchPaperKind;
import hk.ust.comp3021.action.SearchResearcherAction;
import hk.ust.comp3021.action.SearchResearcherAction.SearchResearcherKind;
import hk.ust.comp3021.action.SortPaperAction;
import hk.ust.comp3021.action.SortPaperAction.SortBase;
import hk.ust.comp3021.action.SortPaperAction.SortKind;
import hk.ust.comp3021.action.StatisticalInformationAction;
import hk.ust.comp3021.action.StatisticalInformationAction.InfoKind;
import hk.ust.comp3021.action.UploadPaperAction;
import hk.ust.comp3021.person.Researcher;
import hk.ust.comp3021.person.User;
import hk.ust.comp3021.resource.Comment;
import hk.ust.comp3021.resource.Comment.CommentType;
import hk.ust.comp3021.resource.Label;
import hk.ust.comp3021.resource.Paper;
import hk.ust.comp3021.utils.BibExporter;
import hk.ust.comp3021.utils.BibParser;
import hk.ust.comp3021.utils.Query;
import hk.ust.comp3021.utils.UserRegister;

public class MiniMendeleyEngine {
    private final String defaultBibFilePath = "resources/bibdata/PAData.bib";
    private final HashMap<String, Paper> paperBase = new HashMap<>();
    private final ArrayList<User> users = new ArrayList<>();
    private final ArrayList<Researcher> researchers = new ArrayList<>();

    private final ArrayList<Comment> comments = new ArrayList<>();

    private final ArrayList<Label> labels = new ArrayList<>();

    private final ArrayList<Action> actions = new ArrayList<>();

    private Queue<LabelAction> labelActionsQueue = new LinkedList<>();

    public MiniMendeleyEngine() {
        populatePaperBaseWithDefaultBibFile();
    }

    public void populatePaperBaseWithDefaultBibFile() {
        User user = new User("User_0", "root_user", new Date());
        users.add(user);
        UploadPaperAction action = new UploadPaperAction("Action_0", user, new Date(), defaultBibFilePath);
        processUploadPaperAction(user, action);
        paperBase.putAll(action.getUploadedPapers());
    }

    public String getDefaultBibFilePath() {
        return defaultBibFilePath;
    }

    public HashMap<String, Paper> getPaperBase() {
        return paperBase;
    }

    public ArrayList<User> getUsers() {
        return users;
    }

    public ArrayList<Researcher> getResearchers() {
        return researchers;
    }

    public ArrayList<Comment> getComments() {
        return comments;
    }

    public ArrayList<Label> getLabels() {
        return labels;
    }

    public ArrayList<Action> getActions() {
        return actions;
    }

    public User processUserRegister(String id, String name, Date date) {
        UserRegister ur = new UserRegister(id, name, date);
        User curUser = ur.register();
        users.add(curUser);
        return curUser;
    }

    public Comment processAddCommentAction(User curUser, AddCommentAction action) {
        actions.add(action);
        if (action.getCommentType() == CommentType.COMMENT_OF_COMMENT) {
            String objCommentID = action.getObjectId();
            for (Comment comment : comments) {
                if (objCommentID.equals(comment.getCommentID())) {
                    String commentID = "Comment" + String.valueOf(comments.size() + 1);
                    Comment newComment = new Comment(commentID, action.getTime(), action.getCommentStr(),
                            action.getUser(), action.getCommentType(), action.getObjectId());
                    comments.add(newComment);
                    comment.appendComment(newComment);
                    curUser.appendNewComment(newComment);
                    action.setActionResult(true);
                    return newComment;
                }
            }
        } else if (action.getCommentType() == CommentType.COMMENT_OF_PAPER) {
            String objCommentID = action.getObjectId();
            for (Map.Entry<String, Paper> entry : paperBase.entrySet()) {
                String paperID = entry.getKey();
                if (paperID.equals(objCommentID)) {
                    String commentID = "Comment" + String.valueOf(comments.size() + 1);
                    Comment newComment = new Comment(commentID, action.getTime(), action.getCommentStr(),
                            action.getUser(), action.getCommentType(), action.getObjectId());
                    comments.add(newComment);
                    entry.getValue().appendComment(newComment);
                    curUser.appendNewComment(newComment);
                    action.setActionResult(true);
                    return newComment;
                }
            }
        }
        action.setActionResult(false);
        return null;
    }

    public Label processAddLabelAction(User curUser, AddLabelAction action) {
        actions.add(action);
        String paperID = action.getPaperID();
        String labelID = "Label" + String.valueOf(labels.size() + 1);
        Label newLabel = new Label(labelID, action.getPaperID(), action.getTime(), action.getLabelStr(),
                action.getUser());

        if (paperBase.containsKey(paperID)) {
            paperBase.get(paperID).appendLabelContent(newLabel);
            curUser.appendNewLabel(newLabel);
            labels.add(newLabel);
            action.setActionResult(true);
            return newLabel;
        } else {
            action.setActionResult(false);
            return null;
        }
    }

    public void processDownloadPaperAction(User curUser, DownloadPaperAction action) {
        actions.add(action);
        String path = action.getDownloadPath();
        String content = "";
        HashMap<String, Paper> downloadedPapers = new HashMap<>();
        for (String paperID : action.getPaper()) {
            if (paperBase.containsKey(paperID)) {
                downloadedPapers.put(paperID, paperBase.get(paperID));
            } else {
                action.setActionResult(false);
                return;
            }
        }
        BibExporter exporter = new BibExporter(downloadedPapers, path);
        exporter.export();
        action.setActionResult(!exporter.isErr());
    }

    public ArrayList<Paper> processSearchPaperAction(User curUser, SearchPaperAction action) {
        actions.add(action);
        switch (action.getKind()) {
        case ID:
            for (Map.Entry<String, Paper> entry : paperBase.entrySet()) {
                if (action.getSearchContent().equals(entry.getKey())) {
                    action.appendToActionResult(entry.getValue());
                }
            }
            break;
        case TITLE:
            for (Map.Entry<String, Paper> entry : paperBase.entrySet()) {
                if (action.getSearchContent().equals(entry.getValue().getTitle())) {
                    action.appendToActionResult(entry.getValue());
                }
            }
            break;
        case AUTHOR:
            for (Map.Entry<String, Paper> entry : paperBase.entrySet()) {
                if (entry.getValue().getAuthors().contains(action.getSearchContent())) {
                    action.appendToActionResult(entry.getValue());
                }
            }
            break;
        case JOURNAL:
            for (Map.Entry<String, Paper> entry : paperBase.entrySet()) {
                if (action.getSearchContent().equals(entry.getValue().getJournal())) {
                    action.appendToActionResult(entry.getValue());
                }
            }
            break;
        default:
            break;
        }
        return action.getActionResult();
    }

    public ArrayList<Paper> processSearchPaperActionByLambda(User curUser, SearchPaperAction action) {
        actions.add(action);
        switch (action.getKind()) {
        case ID:
            paperBase.entrySet().forEach(entry -> {
                if (action.isEqual.test(entry.getKey()))
                    action.appendToActionResultByLambda.accept(entry.getValue());
            });
            break;
        case TITLE:
            paperBase.entrySet().forEach(entry -> {
                if (action.isEqual.test(entry.getValue().getTitle()))
                    action.appendToActionResultByLambda.accept(entry.getValue());
            });
            break;
        case AUTHOR:
            paperBase.entrySet().forEach(entry -> {
                if (action.isContain.test(entry.getValue().getAuthors()))
                    action.appendToActionResultByLambda.accept(entry.getValue());
            });
            break;
        case JOURNAL:
            paperBase.entrySet().forEach(entry -> {
                if (action.isEqual.test(entry.getValue().getJournal()))
                    action.appendToActionResultByLambda.accept(entry.getValue());
            });
            break;
        default:
            break;
        }
        return action.getActionResult();
    }

    public List<Paper> processSortPaperActionByLambda(User curUser, SortPaperAction action) {
        actions.add(action);
        paperBase.entrySet().forEach(entry -> {
            action.appendToActionResultByLambda.accept(entry.getValue());
        });
        switch (action.getBase()) {
        case ID:
            action.comparator = (paper1, paper2) -> stringProcessNullSafe(paper1.getPaperID(), paper2.getPaperID());
            if (action.kindPredicate.test(action.getKind()))
                action.comparator = action.comparator.reversed();
            break;
        case TITLE:
            action.comparator = (paper1, paper2) -> stringProcessNullSafe(paper1.getTitle(), paper2.getTitle());
            if (action.kindPredicate.test(action.getKind()))
                action.comparator = action.comparator.reversed();
            break;
        case AUTHOR:
            action.comparator = (paper1, paper2) -> {
                return stringProcessNullSafe(String.join(",", paper1.getAuthors()),
                        String.join(",", paper2.getAuthors()));
            };
            if (action.kindPredicate.test(action.getKind()))
                action.comparator = action.comparator.reversed();
            break;
        case JOURNAL:
            action.comparator = (paper1, paper2) -> stringProcessNullSafe(paper1.getJournal(), paper2.getJournal());
            if (action.kindPredicate.test(action.getKind()))
                action.comparator = action.comparator.reversed();
            break;
        default:
            break;
        }
        action.sortFunc.get();
        return action.getActionResult();
    }

    public HashMap<String, List<Paper>> processSearchResearcherActionByLambda(User curUser,
            SearchResearcherAction action) {
        actions.add(action);
        paperBase.entrySet().forEach(entry -> {
            entry.getValue().getAuthors().forEach(author -> action.appendToActionResult(author, entry.getValue()));
        });
        switch (action.getKind()) {
        case PAPER_WITHIN_YEAR:
            action.searchFunc1.get();
            break;
        case JOURNAL_PUBLISH_TIMES:
            action.searchFunc2.get();
            break;
        case KEYWORD_SIMILARITY:
            action.searchFunc3.get();
            break;

        default:
            break;
        }
        return action.getActionResult();
    }

    int stringProcessNullSafe(String str1, String str2) {
        if (str1 == null && str2 == null)
            return 0;
        if (str1 == null)
            return -1;
        if (str2 == null)
            return 1;
        return str1.compareTo(str2);
    }

    public Map<String, Double> processStatisticalInformationActionByLambda(User curUser,
            StatisticalInformationAction action) {
        actions.add(action);
        List<Paper> paperList = new ArrayList<Paper>();
        paperBase.entrySet().forEach(entry -> paperList.add(entry.getValue()));
        switch (action.getKind()) {
        case AVERAGE:
            action.obtainer1.apply(paperList);
            break;
        case MAXIMAL:
            action.obtainer2.apply(paperList);
            break;
        default:
            break;
        }
        return action.getActionResult();
    }

    public void processUploadPaperAction(User curUser, UploadPaperAction action) {
        actions.add(action);
        BibParser parser = new BibParser(action.getBibfilePath());
        parser.parse();
        action.setUploadedPapers(parser.getResult());
        for (String paperID : action.getUploadedPapers().keySet()) {
            Paper paper = action.getUploadedPapers().get(paperID);
            paperBase.put(paperID, paper);
            for (String researcherName : paper.getAuthors()) {
                Researcher existingResearch = null;
                for (Researcher researcher : researchers) {
                    if (researcher.getName().equals(researcherName)) {
                        existingResearch = researcher;
                        break;
                    }
                }
                if (existingResearch == null) {
                    Researcher researcher = new Researcher("Researcher_" + researchers.size(), researcherName);
                    researcher.appendNewPaper(paper);
                    researchers.add(researcher);
                } else {
                    existingResearch.appendNewPaper(paper);
                }
            }
        }
        action.setActionResult(!parser.isErr());
    }

    /**
     * TODO: Implement the new searching method with Lambda expressions using
     * functional interfaces. The thing you need to do is to implement the three
     * functional interfaces, i.e., searchFunc1 / searchFunc2 /searchFunc3. The
     * prototypes for the functional interfaces are in `SearchResearcherAction`. PS:
     * You should operate directly on `actionResult` since we have already put the
     * papers into it.
     */

    /**
     * TODO Implement code in this function to perform the importation of more than
     * one bib file in parallel
     * @param curUser
     * @param parallelImportAction: an action of parallel import that includes the
     *                              list of path files entered by the user
     */
    public void processParallelImport(User curUser, ParallelImportAction parallelImportAction) {
        actions.add(parallelImportAction);
        parallelImportAction.setCompleted(false);
        if(parallelImportAction.getFilePaths().size() <= ParallelImportAction.maxFileNumber){
            ArrayList<String> validpaths = new ArrayList<>();
            for(int i = 0; i < parallelImportAction.getFilePaths().size(); i++) {
                File file = new File(parallelImportAction.getFilePaths().get(i));
                boolean exists = file.exists();
                if(exists){
                    validpaths.add(parallelImportAction.getFilePaths().get(i));
                }
            }
            parallelImportAction.setFilePaths(validpaths);

            HashMap<String, Paper> importPapers = new HashMap<>();
            Lock lock = new ReentrantLock();
            ArrayList<Thread> threads = new ArrayList<>();
            class UploadPaper implements Runnable{
                private int i;

                UploadPaper(int i) {
                    this.i = i;
                }
                @Override
                public void run() {
                    BibParser parser = new BibParser(parallelImportAction.getFilePaths().get(i));
                    parser.parse();
                    lock.lock();
                    for (String paperID : parser.getResult().keySet()) {
                        Paper paper = parser.getResult().get(paperID);
                        importPapers.put(paperID, paper);
                        for (String researcherName : paper.getAuthors()) {
                            Researcher existingResearch = null;
                            for (Researcher researcher : researchers) {
                                if (researcher.getName().equals(researcherName)) {
                                    existingResearch = researcher;
                                    break;
                                }
                            }
                            if (existingResearch == null) {
                                Researcher researcher = new Researcher("Researcher_" + researchers.size(), researcherName);
                                researcher.appendNewPaper(paper);
                                researchers.add(researcher);
                            } else {
                                existingResearch.appendNewPaper(paper);
                            }

                        }
                    }
                    parallelImportAction.setImportedPapers(importPapers);
                    paperBase.putAll(parallelImportAction.getImportedPapers());
                    lock.unlock();
                }
            }

            for(int i = 0; i < parallelImportAction.getFilePaths().size(); i++){
                Thread parallelImportThread = new Thread(new UploadPaper(i));
                threads.add(parallelImportThread);
            }

            for(int i = 0; i < threads.size(); i++){
                threads.get(i).start();
            }

            for(int i = 0; i < threads.size(); i++){
                try {
                    threads.get(i).join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            parallelImportAction.setCompleted(true);
        }
    }

    /**
     * TODO Implement this function using only 5 threads to search papers for
     * the @words in @paperBase and store the results in @results variable
     * in @multipleSearch At the end, print the title and paperId of the papers that
     * matches the search and print the number of results. Store the number of found
     * results in @foundResults in @multipleSearch
     * @param curUser
     * @param multipleSearch:  the list of words to search for
     * @throws InterruptedException
     */
    public void processMultiKeywordSearch(User curUser, SearchMultipleKeywordsAction multipleSearch)
            throws InterruptedException {
        actions.add(multipleSearch);
        multipleSearch.setFound(false);
        Lock lock = new ReentrantLock();
        class SearchKeyword implements Runnable{
            private String s;

            SearchKeyword(String s) {
                this.s = s;
            }
            @Override
            public void run() {
                for (String paperID: paperBase.keySet()) {
                    if(paperBase.get(paperID).getAbsContent() != null) {
                        if (paperBase.get(paperID).getAbsContent().contains(s)) {
                            lock.lock();
                            multipleSearch.setFound(true);
                            multipleSearch.increaseFound();
                            multipleSearch.addFoundResult(paperBase.get(paperID));
                            lock.unlock();
                            continue;
                        }
                    }

                    if (paperBase.get(paperID).getTitle() != null) {
                        if (paperBase.get(paperID).getTitle().contains(s)) {
                            lock.lock();
                            multipleSearch.setFound(true);
                            multipleSearch.increaseFound();
                            multipleSearch.addFoundResult(paperBase.get(paperID));
                            lock.unlock();
                            continue;
                        }
                    }

                    if (paperBase.get(paperID).getKeywords() != null) {
                        if (paperBase.get(paperID).getKeywords().contains(s)) {
                            lock.lock();
                            multipleSearch.setFound(true);
                            multipleSearch.increaseFound();
                            multipleSearch.addFoundResult(paperBase.get(paperID));
                            lock.unlock();
                        }
                    }
                }
            }
        }

        if(multipleSearch.getWords().size() <= 20){
            ArrayList<String> validwords = new ArrayList<>();
            for(int i = 0; i < multipleSearch.getWords().size(); i++) {
               if(!validwords.contains(multipleSearch.getWords().get(i))){
                   validwords.add(multipleSearch.getWords().get(i));
               }
            }
            multipleSearch.setWords(validwords);
            if(validwords.size() <= SearchMultipleKeywordsAction.numThreads){
                ArrayList<Thread> threads = new ArrayList<>();
                for(int i = 0; i < multipleSearch.getWords().size(); i++){
                    Thread multipleSearchThread = new Thread(new SearchKeyword(multipleSearch.getWords().get(i)));
                    threads.add(multipleSearchThread);
                }

                for(int i = 0; i < threads.size(); i++){
                    threads.get(i).start();
                }

                for(int i = 0; i < threads.size(); i++){
                    try {
                        threads.get(i).join();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                System.out.println("Number of found results: "+ multipleSearch.getFoundResult());

            }else if (validwords.size() > SearchMultipleKeywordsAction.numThreads){
                ArrayList<Thread> threads = new ArrayList<>();
                int rounds = multipleSearch.getWords().size() / SearchMultipleKeywordsAction.numThreads;
                int finalRoundThreads = multipleSearch.getWords().size() % SearchMultipleKeywordsAction.numThreads;
                int i = 0;
                while (i < rounds){
                    for(int j = 0; j < SearchMultipleKeywordsAction.numThreads; j++){
                        Thread multipleSearchThread = new Thread(new SearchKeyword(multipleSearch.getWords().
                                get(j + SearchMultipleKeywordsAction.numThreads * i)));
                        threads.add(multipleSearchThread);
                    }

                    for(int j = 0; j < threads.size(); j++){
                        threads.get(j).start();
                    }

                    for(int j = 0; j < threads.size(); j++){
                        try {
                            threads.get(j).join();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    i++;
                    threads.clear();

                }

                if(finalRoundThreads > 0){
                    for(int j = 0; j < finalRoundThreads; j++){
                        Thread multipleSearchThread = new Thread(new SearchKeyword(multipleSearch.getWords().
                                get(j + SearchMultipleKeywordsAction.numThreads * i)));
                        threads.add(multipleSearchThread);
                    }

                    for(int j = 0; j < threads.size(); j++){
                        threads.get(j).start();
                    }

                    for(int j = 0; j < threads.size(); j++){
                        try {
                            threads.get(j).join();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }


                System.out.println("Number of found results: "+ multipleSearch.getFoundResult());
            }
        }
    }

    /***
     * TODO Implement the code for this method to perform the creation of a new
     * label for a paper.
     * @param curUser
     * @param actionList: holds the list of actions to be performed one after another
     */
    public Runnable processAddLabel(User curUser, LabelActionList actionList) {
        return new Runnable() {

            @Override
            public void run() {
                while(true){
                    try {
                        if (actionList.getHead() != null){
                            if (actionList.getHead().getActionType() == Action.ActionType.ADD_LABEL) {
                                System.out.println(actionList.getHead().getActionType().toString() + " Action Start");
                                LabelAction currentAction = actionList.dequeue();
                                actions.add(currentAction);
                                ArrayList<String> labelList = new ArrayList<>();
                                labelList.addAll(processInputLabels(currentAction.getLabel()));
                                for (int i = 0; i < labelList.size(); i++) {
                                    boolean duplicated = false;
                                    String labelID = "Label" + String.valueOf(labels.size() + 1);
                                    Label label = new Label(labelID, currentAction.getPaperID(), currentAction.
                                            getTime(), labelList.get(i), curUser);
                                    if (paperBase.containsKey(currentAction.getPaperID())) {
                                        for(Label paperLabel : paperBase.get(currentAction.getPaperID()).getLabels()){
                                            if(paperLabel.getContent() == label.getContent()){
                                                duplicated = true;
                                                break;
                                            }
                                        }
                                        if(!duplicated){
                                            paperBase.get(currentAction.getPaperID()).appendLabelContent(label);
                                            curUser.appendNewLabel(label);
                                            labels.add(label);
                                            actionList.increateNumOfAdded();
                                            actionList.addProcessedLabel(label.getContent());
                                            System.out.println("New Label \'" + label.getContent() +
                                                    "\' added to Paper: " + currentAction.getPaperID());
                                        }
                                    }
                                }
                                currentAction.setSuccessful(true);
                                synchronized (curUser) {
                                    curUser.notifyAll();
                                }
                            }else {
                                synchronized (curUser) {
                                    curUser.wait();
                                }
                            }
                        }else{
                            break;
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
    }

    /***
     * TODO Implement the code for this method to perform the updating of a new
     * label for a paper
     * @param curUser
     * @param actionList: holds the list of actions to be performed one after another
     */
    public Runnable processUpdateLabel(User curUser, LabelActionList actionList) {
        return new Runnable() {
            @Override
            public void run() {
                while(true){
                    try {
                        if (actionList.getHead() != null){
                            if (actionList.getHead().getActionType() == Action.ActionType.UPDATE_LABELS){
                                System.out.println(actionList.getHead().getActionType().toString() + " Action Start");
                                LabelAction currentAction = actionList.dequeue();
                                actions.add(currentAction);
                                ArrayList<String> updatelabelList = new ArrayList<>();
                                updatelabelList.addAll(processInputLabels(currentAction.getLabel()));
                                String newlabel = currentAction.getNewLabel();
                                for (String paperID : paperBase.keySet()) {
                                    boolean updated = false;
                                    for (String labelContent : updatelabelList) {
                                        for (Label label : paperBase.get(paperID).getLabels()) {
                                            if(label.getContent() == labelContent){
                                                if(!updated){
                                                    String deletedlabel = label.getContent();
                                                    paperBase.get(paperID).getLabels().remove(label);
                                                    labels.remove(label);
                                                    String labelID = "Label" + String.valueOf(labels.size() + 1);
                                                    Label updatedlabel = new Label(labelID,  paperID,
                                                            currentAction.getTime(), newlabel, curUser);
                                                    paperBase.get(paperID).getLabels().add(updatedlabel);
                                                    labels.add(updatedlabel);
                                                    actionList.increateNumOfupdated();
                                                    actionList.addProcessedLabel(deletedlabel);
                                                    System.out.println("Label \'" + deletedlabel
                                                            + "\' is updated to \'" + updatedlabel.getContent() + "\'.");
                                                }else {
                                                    String deletedlabel = label.getContent();
                                                    paperBase.get(paperID).getLabels().remove(label);
                                                    labels.remove(label);
                                                    actionList.increateNumOfupdated();
                                                    actionList.addProcessedLabel(deletedlabel);
                                                    System.out.println("Label \'" + deletedlabel
                                                            + "\' is updated to \'" + newlabel + "\'.");
                                                }
                                                break;
                                            }
                                        }
                                    }
                                }
                                currentAction.setSuccessful(true);
                                synchronized (curUser) {
                                    curUser.notifyAll();
                                }
                            }else {
                                synchronized (curUser) {
                                    curUser.wait();
                                }
                            }
                        }else {
                            break;
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
    }

    /***
     * TODO
     * Implement the code for this method to perform the deletion of the label of a paper
     * @param curUser
     * @param actionList: holds the list of actions to be performed one after another
     */
    public Runnable processDeleteLabel(User curUser, LabelActionList actionList) {
        return new Runnable() {
            @Override
            public void run() {
                while(true){
                    try {
                        if (actionList.getHead() != null){
                            if (actionList.getHead().getActionType() == Action.ActionType.DELETE_LABELS) {
                                System.out.println(actionList.getHead().getActionType().toString() + " Action Start");
                                LabelAction currentAction = actionList.dequeue();
                                actions.add(currentAction);
                                ArrayList<String> labelList = new ArrayList<>();
                                labelList.addAll(processInputLabels(currentAction.getLabel()));
                                for (String paperID : paperBase.keySet()) {
                                    for (String labelContent : labelList) {
                                        for (Label label : paperBase.get(paperID).getLabels()) {
                                            if (label.getContent() == labelContent) {
                                                paperBase.get(paperID).getLabels().remove(label);
                                                labels.remove(label);
                                                actionList.increateNumOfDeleted();
                                                actionList.addProcessedLabel(label.getContent());
                                                System.out.println("Label \'" + label.getContent() + "\'in Paper: "
                                                        + paperID + " is deleted");
                                                break;
                                            }
                                        }
                                    }
                                }
                                currentAction.setSuccessful(true);
                                synchronized (curUser){
                                    curUser.notifyAll();
                                }
                            }else {
                                synchronized (curUser){
                                    curUser.wait();
                                }
                            }
                        }else {
                            break;
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
    }


    /**
     * TODO Implement the code for reading queries from a file and process the queries
        in an efficient manner with highest
     * performance with the use of multithreading.
     * @param curUser
     * @para action: the action for handling settins of one iteration of query processing from a specific file
     **/
    public void processConcurrentQuery(User curUser, QueryAction action) {
        actions.add(action);
        try {
            BufferedReader reader = new BufferedReader(new FileReader(action.getFilePath()));
            String line = reader.readLine();
            while (line != null) {
                if (line.isEmpty()) {
                    line = reader.readLine();
                    continue;
                } else {
                    line = line.trim();
                    Query query = new Query(line);
                    action.addQuery(query);
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int threadsize = action.getQueries().size();
        Thread[] threads = new Thread[threadsize];

        int turn = 0;
        while (turn < action.getQueries().size()) {
            if(action.getQueries().size() - turn < threads.length){
                for (int i = 0; i < action.getQueries().size() - turn; i++) {
                    int threadTurn = turn;
                    threads[i] = new Thread(createRunnable(threadTurn,threadsize,action,threads));
                    threads[i].start();
                    turn++;
                }
                for (int i = 0; i < action.getQueries().size() - turn; i++) {
                    try {
                        threads[i].join();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }else {
                for (int i = 0; i < threads.length; i++) {
                    int threadTurn = turn;
                    threads[i] = new Thread(createRunnable(threadTurn,threadsize,action,threads));
                    threads[i].start();
                    turn++;
                }
                for (int i = 0; i < threads.length; i++) {
                    try {
                        threads[i].join();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        action.setCompleted(true);
    }

    public User userInterfaceForUserCreation() {
        System.out.println("Please enter your name.");
        Scanner scan2 = new Scanner(System.in);
        if (scan2.hasNextLine()) {
            String name = scan2.nextLine();
            System.out.println("Create the account with the name: " + name);
            String userID = "User_" + users.size();
            User curUser = processUserRegister(userID, name, new Date());
            System.out.println("Account created!");
            return curUser;
        }
        return null;
    }

    public void userInterfaceForPaperSearch(User curUser) {
        System.out.println("Please specify the search kind:");
        System.out.println("  1: Search by ID");
        System.out.println("  2: Search by title");
        System.out.println("  3: Search by author");
        System.out.println("  4: Search by journal");
        while (true) {
            Scanner scan3 = new Scanner(System.in);
            if (scan3.hasNextInt()) {
                int k = scan3.nextInt();
                if (k < 1 || k > 4) {
                    System.out.println("You should enter 1~4.");
                } else {
                    System.out.println("Please specify the search word:");
                    Scanner scan4 = new Scanner(System.in);
                    if (scan4.hasNextLine()) {
                        String word = scan4.nextLine();
                        SearchPaperAction action = new SearchPaperAction("Action_" + actions.size(), curUser,
                                new Date(), word, SearchPaperKind.values()[k - 1]);
                        actions.add(action);
                        processSearchPaperAction(curUser, action);

                        if (action.getActionResult().size() > 0) {
                            System.out.println("Paper found! The paper IDs are as follows:");
                            for (Paper paper : action.getActionResult()) {
                                System.out.println(paper.getPaperID());
                            }
                        } else {
                            System.out.println("Paper not found!");
                        }
                        break;
                    }
                }
            }
        }
    }

    public void userInterfaceForPaperSearchByLambda(User curUser) {
        System.out.println("Please specify the search kind:");
        System.out.println("  1: Search by ID");
        System.out.println("  2: Search by title");
        System.out.println("  3: Search by author");
        System.out.println("  4: Search by journal");
        while (true) {
            Scanner scan1 = new Scanner(System.in);
            if (scan1.hasNextInt()) {
                int k = scan1.nextInt();
                if (k < 1 || k > 4) {
                    System.out.println("You should enter 1~4.");
                } else {
                    System.out.println("Please specify the search word:");
                    Scanner scan2 = new Scanner(System.in);
                    if (scan2.hasNextLine()) {
                        String word = scan2.nextLine();
                        SearchPaperAction action = new SearchPaperAction("Action_" + actions.size(), curUser,
                                new Date(), word, SearchPaperKind.values()[k - 1]);
                        actions.add(action);
                        processSearchPaperActionByLambda(curUser, action);

                        if (action.getActionResult().size() > 0) {
                            System.out.println("Paper found! The paper IDs are as follows:");
                            for (Paper paper : action.getActionResult()) {
                                System.out.println(paper);
                            }
                        } else {
                            System.out.println("Paper not found!");
                        }
                        break;
                    }
                }
            }
        }
    }

    public void userInterfaceForPaperSortByLambda(User curUser) {
        System.out.println("Please specify the sort base:");
        System.out.println("  1: Sort by ID");
        System.out.println("  2: Sort by title");
        System.out.println("  3: Sort by author");
        System.out.println("  4: Sort by journal");
        while (true) {
            Scanner scan1 = new Scanner(System.in);
            if (scan1.hasNextInt()) {
                int k = scan1.nextInt();
                if (k < 1 || k > 4) {
                    System.out.println("You should enter 1~4.");
                } else {
                    System.out.println("Please specify the sort kind:");
                    System.out.println("  1: Sort in ascending order");
                    System.out.println("  2: Sort in descending order");
                    Scanner scan2 = new Scanner(System.in);
                    if (scan2.hasNextLine()) {
                        int m = scan2.nextInt();
                        SortPaperAction action = new SortPaperAction("Action_" + actions.size(), curUser,
                                new Date(), SortBase.values()[k - 1], SortKind.values()[m - 1]);
                        actions.add(action);
                        processSortPaperActionByLambda(curUser, action);

                        if (action.getActionResult().size() > 0) {
                            System.out.println("Paper sorted! The paper are sorted as follows:");
                            for (Paper paper : action.getActionResult()) {
                                System.out.println(paper);
                            }
                        } else {
                            System.out.println("Paper not sorted!");
                        }
                        break;
                    }
                }
            }
        }
    }

    public void userInterfaceForResearcherSearchByLambda(User curUser) {
        System.out.println("Please specify the search kind:");
        System.out.println("  1: Search researchers who publish papers more than X times " +
                "in the recent Y years");
        System.out.println(
                "  2: Search researchers whose papers published " +
                        "in the journal X have abstracts more than Y words");
        System.out.println(
                "  3: Search researchers whoes keywords have more than similarity X% " +
                        "as one of those of the researcher Y");
        while (true) {
            Scanner scan1 = new Scanner(System.in);
            if (scan1.hasNextInt()) {
                int k = scan1.nextInt();
                if (k < 1 || k > 3) {
                    System.out.println("You should enter 1~3.");
                } else {
                    System.out.println("Please specify the X:");
                    Scanner scan2 = new Scanner(System.in);
                    if (scan2.hasNextLine()) {
                        String factorX = scan2.nextLine();
                        System.out.println("Please specify the Y:");
                        Scanner scan3 = new Scanner(System.in);
                        if (scan3.hasNextLine()) {
                            String factorY = scan3.nextLine();
                            SearchResearcherAction action = new SearchResearcherAction("Action_" +
                                    actions.size(), curUser, new Date(), factorX, factorY,
                                    SearchResearcherKind.values()[k - 1]);
                            actions.add(action);
                            processSearchResearcherActionByLambda(curUser, action);

                            if (action.getActionResult().size() > 0) {
                                System.out.println("Researcher found! The researcher information is as follows:");
                                for (Map.Entry<String, List<Paper>> entry : action.getActionResult().entrySet()) {
                                    System.out.println(entry.getKey());
                                    for (Paper paper : entry.getValue()) {
                                        System.out.println(paper);
                                    }
                                }
                            } else {
                                System.out.println("Researcher not found!");
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    public void userInterfaceForStatisticalInformationByLambda(User curUser) {
        System.out.println("Please specify the information:");
        System.out.println("  1: Obtain the average number of papers published by researchers per year");
        System.out.println("  2: Obtain the journals that receive the most papers every year");
        while (true) {
            Scanner scan1 = new Scanner(System.in);
            if (scan1.hasNextInt()) {
                int k = scan1.nextInt();
                if (k < 1 || k > 2) {
                    System.out.println("You should enter 1~2.");
                } else {
                    StatisticalInformationAction action = new StatisticalInformationAction("Action_"
                            + actions.size(), curUser, new Date(), InfoKind.values()[k - 1]);
                    actions.add(action);
                    processStatisticalInformationActionByLambda(curUser, action);

                    if (action.getActionResult().size() > 0) {
                        System.out.println("Information Obtained! The information is as follows:");
                        for (Map.Entry<String, Double> entry : action.getActionResult().entrySet()) {
                            System.out.println(entry.getKey() + ": " + entry.getValue());
                        }
                    } else {
                        System.out.println("Information not obtained!");
                    }
                    break;
                }
            }
        }
    }

    public void userInterfaceForPaperUpload(User curUser) {
        System.out.println("Please specify the absolute path of the bib file:");
        Scanner scan5 = new Scanner(System.in);
        if (scan5.hasNextLine()) {
            String name = scan5.nextLine();
            UploadPaperAction action = new UploadPaperAction("Action_" + actions.size(),
                    curUser, new Date(), name);
            actions.add(action);
            processUploadPaperAction(curUser, action);
            if (action.getActionResult()) {
                System.out.println("Succeed! The uploaded papers are as follows:");
                for (String id : action.getUploadedPapers().keySet()) {
                    System.out.println(id);
                }
            } else {
                System.out.println("Fail! You need to specify an existing bib file.");
            }
        }
    }

    public void userInterfaceForPaperDownload(User curUser) {
        System.out.println("Please specify the absolute path of the bib file:");
        Scanner scan6 = new Scanner(System.in);
        if (scan6.hasNextLine()) {
            String path = scan6.nextLine();
            DownloadPaperAction action = new DownloadPaperAction("Action_" + actions.size(),
                    curUser, new Date(), path);
            System.out.println("Please enter the paper ID line by line and end with END");
            while (true) {
                Scanner scan7 = new Scanner(System.in);
                if (scan7.hasNextLine()) {
                    String name = scan7.nextLine();
                    if (name.equals("END")) {
                        break;
                    } else {
                        action.appendPapers(name);
                    }
                }
            }
            actions.add(action);
            processDownloadPaperAction(curUser, action);
            if (action.getActionResult()) {
                System.out.println("Succeed! The downloaded paper is stored in your specified file.");
            } else {
                System.out.println("Fail! Some papers not found!");
            }
        }
    }

    public void userInterfaceForAddLabel(User curUser) {
        System.out.println("Please specify the paper ID:");
        Scanner scan8 = new Scanner(System.in);
        if (scan8.hasNextLine()) {
            String paperID = scan8.nextLine();
            System.out.println("Please specify the label");
            Scanner scan9 = new Scanner(System.in);
            if (scan9.hasNextLine()) {
                String newlabel = scan9.nextLine();
                AddLabelAction action = new AddLabelAction("Action_" + actions.size(),
                        curUser, new Date(), newlabel, paperID);
                actions.add(action);
                processAddLabelAction(curUser, action);

                if (action.getActionResult()) {
                    System.out.println("Succeed! The label is added.");
                } else {
                    System.out.println("Fail!");
                }
            }
        }
    }

    public void userInterfaceForAddComment(User curUser) {
        System.out.println("Please specify the commented object ID:");
        Scanner scan10 = new Scanner(System.in);
        if (scan10.hasNextLine()) {
            String objID = scan10.nextLine();
            System.out.println("Please specify the comment");
            Scanner scan11 = new Scanner(System.in);
            if (scan11.hasNextLine()) {
                String newCommentStr = scan11.nextLine();
                CommentType t = null;
                if (objID.startsWith("Comment")) {
                    t = CommentType.COMMENT_OF_COMMENT;
                } else {
                    t = CommentType.COMMENT_OF_PAPER;
                }
                AddCommentAction action = new AddCommentAction("Action_" + actions.size(), curUser,
                        new Date(), newCommentStr, t, objID);
                actions.add(action);
                processAddCommentAction(curUser, action);

                if (action.getActionResult()) {
                    System.out.println("Succeed! The comment is added.");
                } else {
                    System.out.println("Fail!");
                }
            }
        }
    }

    /**
     * TODO Implement the logic for inferring the absolute path of the files from
     * the user input
     *
     * @param curUser: current user who is performing this action
     */
    private void userInterfaceForParallelImport(User curUser) {
        System.out.println("Please specify the absolute path of the bib files to import " +
                "in one line separated by \",\" (e.g. /temp/1.bib,/temp/2.bib):");

        Scanner scan10 = new Scanner(System.in);
        ParallelImportAction parallelImport = new ParallelImportAction("Action_"
                + actions.size(), curUser, new Date());

        if (scan10.hasNextLine()) {
            String name = scan10.nextLine();
            String [] filepath = name.split(",");
            ArrayList<String> filePaths = new ArrayList<>();
            for (int i = 0; i < filepath.length; i++) {
                filePaths.add(filepath[i]);
            }

            parallelImport.setFilePaths(filePaths);
        }
        processParallelImport(curUser, parallelImport);
    }

    /***
     * TODO
     * Implement code in this function to receive the searching keywords from the user
     * @param curUser
     * @throws InterruptedException
     */
    private void userInterfaceForMultiKeywordSearch(User curUser) throws InterruptedException {
        System.out.println(
                "Please enter at most 20 keywords for searching separated by \"+ \" (e.g. word1 + word2 + word3):");
        ArrayList<String> words = new ArrayList();
        SearchMultipleKeywordsAction searchMultipleKeywordsAction = new SearchMultipleKeywordsAction(
                "Action_" + actions.size(), curUser, new Date());
        Scanner scan11 = new Scanner(System.in);
        if (scan11.hasNextLine()) {
            String wordLine = scan11.nextLine();
            String [] keywords = wordLine.split("\\+");
            for (int i = 0; i < keywords.length; i++) {
                words.add(keywords[i]);
            }

            searchMultipleKeywordsAction.setWords(words);
        }

        processMultiKeywordSearch(curUser, searchMultipleKeywordsAction);
    }

    /**
     * TODO In this function, the program interactively asks the user @curUser for
     * adding, updating or removing labels and performs the operation in the
     * background
     *
     * @param curUser
     */
    private void userInterfaceModifyLabels(User curUser) {
        boolean exit = false;
        Thread[] threads = new Thread[3];
        LabelActionList labelActionList = new LabelActionList();

        while (!exit) {
            System.out.println(
                    "Please choose from the below operations: (1) Add a label (2) Update a label " +
                            "(3) Delete a label (4) Exit:");

            @SuppressWarnings("resource")
            Scanner scan13 = new Scanner(System.in);

            if (scan13.hasNextInt()) {
                int k = scan13.nextInt();
                if (k < 1 || k > 4) {
                    System.out.println("You should enter 1~4.");
                } else {
                    switch (k) {
                        case 1:
                            // TODO Implement code to add new labels
                            System.out.println("Please enter the paperId:");
                            scan13 = new Scanner(System.in);
                            if (scan13.hasNextLine()) {
                                String paperId = scan13.nextLine();
                                System.out.println(
                                        "Please enter the target labels to update separated by \",\" " +
                                                "(e.g, label1,label2,label3, ... :");
                                Scanner scan16 = new Scanner(System.in);
                                if (scan16.hasNextLine()) {
                                    String labels = scan16.nextLine();
                                    LabelAction labelAction = new LabelAction("Action_" + actions.size(),
                                            curUser, new Date(), Action.ActionType.ADD_LABEL, labels);
                                    labelAction.setPaperID(paperId);
                                    this.labelActionsQueue.add(labelAction);
                                    labelActionList.enqueue(labelAction);
                                    threads[0] = new Thread(processAddLabel(curUser, labelActionList));
                                    threads[0].start();

                                }
                            }

                            break;

                        case 2:
                            System.out.println(
                                    "Please enter the target labels to update separated by \",\" " +
                                            "(e.g, label1,label2,label3, ... :");
                            Scanner scan16 = new Scanner(System.in);
                            if (scan13.hasNextLine()) {
                                String labels = scan13.nextLine();
                                ArrayList<String> inputLabels = new ArrayList<>();
                                inputLabels.addAll(processInputLabels(labels));

                                if (inputLabels.size() > 0) {
                                    String newlabel = "";
                                    System.out.println("Please enter the new label:");
                                    scan13 = new Scanner(System.in);
                                    if (scan13.hasNextLine()) {
                                        newlabel = scan13.nextLine();
                                        // TODO Implement code to update @inputLabels labels with @newlabel
                                        LabelAction labelAction = new LabelAction("Action_" + actions.size(),
                                                curUser, new Date(), Action.ActionType.UPDATE_LABELS, labels);
                                        labelAction.setNewLabel(newlabel);
                                        this.labelActionsQueue.add(labelAction);
                                        labelActionList.enqueue(labelAction);
                                        threads[1] = new Thread(processUpdateLabel(curUser, labelActionList));
                                        threads[1].start();

                                    }
                                } else {
                                    System.out.println("Fail: no input label is entered!");
                                }

                            } else {
                                System.out.println("Fail: Please enter the input labels.");
                            }
                            break;

                        case 3:
                            System.out.println(
                                    "Please the target labels to reomve separate by \",\" " +
                                            "(e.g, label1,label2,label3, ... :");
                            scan13 = new Scanner(System.in);
                            if (scan13.hasNextLine()) {
                                String labels = scan13.nextLine();
                                // TODO Implement code to remove @inputLabels
                                LabelAction labelAction = new LabelAction("Action_" + actions.size(), curUser,
                                        new Date(), Action.ActionType.DELETE_LABELS, labels);
                                this.labelActionsQueue.add(labelAction);
                                labelActionList.enqueue(labelAction);
                                threads[2] = new Thread(processDeleteLabel(curUser, labelActionList));
                                threads[2].start();

                            }
                            break;

                        case 4:
                            exit = true;
                            try {
                                for(int i = 0; i < 3; i++){
                                    threads[i].join();
                                }

                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            labelActionList.setFinished(true);
                            break;

                        default:
                            break;
                    }
                }
            }
        }
    }

    /***
     *
     * TODO Implement the code for extracting the labels from the input string
     *
     * @param labels
     * @return
     */
    private ArrayList<String> processInputLabels(String labels) {
        // TODO Auto-generated method stub
        ArrayList<String> labelList = new ArrayList<String>();
        String[] labelArray = labels.split(",");
        for(int i = 0; i < labelArray.length; i++){
            if(!labelList.contains(labelArray[i])){
                labelList.add(labelArray[i]);
            }
        }
        return labelList;
    }

    /***
     * TODO Implement the code to get the absolute path of the file consisting of the queries and process
     * each query
     *
     * @param curUser
     */

    public void userInterfaceConcurrentQueryProcess(User curUser) {
        QueryAction action = null;
        System.out.println("Please specify the absolute path of the file containing the queries:");
        // Retrieve the file locations from @name
        Scanner scan13 = new Scanner(System.in);
        if (scan13.hasNextLine()) {
            String name = scan13.nextLine();
            action = new QueryAction("Action_" + actions.size(), curUser, new Date(), Action.ActionType.PROCESS_QUERY);
            action.setFilePath(name);
            processConcurrentQuery(curUser, action);
        } else {
            System.out.println("Fail: No filepath is entered");
        }

    }

    public void userInterface() throws InterruptedException {
        System.out.println("----------------------------------------------------------------------");
        System.out.println("MiniMendeley is running...");
        System.out.println("Initial paper base has been populated!");
        User curUser = null;

        while (true) {
            System.out.println("----------------------------------------------------------------------");
            System.out.println("Please select the following operations with the corresponding numbers:");
            System.out.println("  0: Register an account");
            System.out.println("  1: Search papers");
            System.out.println("  2: Upload papers");
            System.out.println("  3: Download papers");
            System.out.println("  4: Add labels");
            System.out.println("  5: Add comments");
            System.out.println("  6: Search papers via Lambda");
            System.out.println("  7: Sort papers via Lambda");
            System.out.println("  8: Search researchers via Lambda");
            System.out.println("  9: Obtain statistical information via Lambda");
            System.out.println("  10: Import several bib files in parallel");
            System.out.println("  11: Multiple Keyword Search");
            System.out.println("  12: Update or Delete Labels");
            System.out.println("  13: Parallel Query Execution");
            System.out.println("  14: Exit");
            System.out.println("----------------------------------------------------------------------");
            Scanner scan1 = new Scanner(System.in);
            if (scan1.hasNextInt()) {
                int i = scan1.nextInt();
                if (i < 0 || i > 14) {
                    System.out.println("You should enter 0~11.");
                    continue;
                }
                if (curUser == null && i != 0) {
                    System.out.println("You need to register an account first.");
                    continue;
                }
                switch (i) {
                case 0: {
                    curUser = userInterfaceForUserCreation();
                    break;
                }
                case 1: {
                    userInterfaceForPaperSearch(curUser);
                    break;
                }
                case 2: {
                    userInterfaceForPaperUpload(curUser);
                    break;
                }
                case 3: {
                    userInterfaceForPaperDownload(curUser);
                    break;
                }
                case 4: {
                    userInterfaceForAddLabel(curUser);
                    break;
                }
                case 5: {
                    userInterfaceForAddComment(curUser);
                    break;
                }
                case 6: {
                    userInterfaceForPaperSearchByLambda(curUser);
                    break;
                }
                case 7: {
                    userInterfaceForPaperSortByLambda(curUser);
                    break;
                }
                case 8: {
                    userInterfaceForResearcherSearchByLambda(curUser);
                    break;
                }
                case 9: {
                    userInterfaceForStatisticalInformationByLambda(curUser);
                    break;
                }
                case 10: {
                    userInterfaceForParallelImport(curUser);
                    break;
                }

                case 11: {
                    userInterfaceForMultiKeywordSearch(curUser);
                }
                    break;
                case 12: {
                    userInterfaceModifyLabels(curUser);
                }
                    break;
                case 13: {
                    userInterfaceConcurrentQueryProcess(curUser);
                }
                    break;
                case 14: {
                    try {
                        userInterfaceForMultiKeywordSearch(curUser);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                default:
                    break;
                }
                if (i == 14)
                    break;
            } else {
                System.out.println("You should enter integer 0~6.");
            }
        }
    }

    public Runnable createRunnable(int threadTurn, int threadsize, QueryAction action, Thread[] threads){
        Runnable newRunnable = new Runnable() {
            int queryNum;

            public void init(int queryNum) {
                this.queryNum = queryNum;
            }

            @Override
            public void run() {
                init(threadTurn);
                if (action.getQueries().get(queryNum).getValidity()) {
                    if (action.getQueries().get(queryNum).getType() == Query.QueryType.ADD) {
                        processAddQuery(action, queryNum, threadsize, threads);

                    } else if (action.getQueries().get(queryNum).getType() == Query.QueryType.UPDATE) {
                        processUpdateQuery(action, queryNum, threadsize, threads);

                    } else if (action.getQueries().get(queryNum).getType() == Query.QueryType.REMOVE) {
                        processRemoveQuery(action, queryNum, threadsize, threads);
                    }
                }else {
                    if (queryNum% threadsize != 0) {
                        synchronized (threads[queryNum% threadsize]) {
                            if (threads[queryNum% threadsize - 1].isAlive()) {
                                try {
                                    threads[queryNum% threadsize].wait();
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }

                    if (queryNum % threadsize + 1 != threadsize) {
                        synchronized (threads[queryNum % threadsize + 1]) {
                            threads[queryNum % threadsize + 1].notify();
                            System.out.println("Count: " + queryNum + "Thread-" + (queryNum % threadsize)
                                    + " notifying Thread-" + (queryNum % threadsize + 1)  + action.getQueries()
                                    .get(queryNum).getQuery());
                        }
                    }
                }
            }
        };
        return newRunnable;
    }

    public void processAddQuery(QueryAction action, int queryNum, int threadsize, Thread[] threads){
        String query = action.getQueries().get(queryNum).getValue();
        if (query.contains("{") && query.contains("}")) {
            query = query.substring(1, query.length() - 2);
        }
        String[] tokens = query.split(",");
        Paper currentPaper = null;
        String paperID = null;
        int j = 0;
        while (tokens[j] != null) {
            if (tokens[j].startsWith("paperID")) {
                paperID = tokens[j].substring(tokens[j].indexOf("\""),
                        tokens[j].length() - 2);
                currentPaper = new Paper(paperID);
            } else {
                tokens[j] = tokens[j].trim();
                if (tokens[j].startsWith("author")) {
                    tokens[j] = tokens[j].substring(tokens[j].indexOf("\""),
                            tokens[j].length() - 2);
                    String[] authors = tokens[j].split(" and ");
                    currentPaper.setAuthors(new ArrayList<>(Arrays.asList(authors)));
                } else if (tokens[j].startsWith("keywords")) {
                    tokens[j] = tokens[j].substring(tokens[j].indexOf("\""),
                            tokens[j].length() - 2);
                    String[] keywords = tokens[j].split(",");
                    currentPaper.setKeywords(new ArrayList<>(Arrays.asList(keywords)));
                } else {
                    if (tokens[j].startsWith("title")) {
                        tokens[j] = tokens[j].substring(tokens[j].indexOf("\""),
                                tokens[j].length() - 2);
                        currentPaper.setTitle(tokens[j]);
                    } else if (tokens[j].startsWith("doi")) {
                        tokens[j] = tokens[j].substring(tokens[j].indexOf("\""),
                                tokens[j].length() - 2);
                        currentPaper.setDoi(tokens[j]);
                    } else if (tokens[j].startsWith("journal")) {
                        tokens[j] = tokens[j].substring(tokens[j].indexOf("\""),
                                tokens[j].length() - 2);
                        currentPaper.setJournal(tokens[j]);
                    } else if (tokens[j].startsWith("year")) {
                        tokens[j] = tokens[j].substring(tokens[j].indexOf("\""),
                                tokens[j].length() - 2);
                        currentPaper.setYear(Integer.parseInt(tokens[j]));
                    } else if (tokens[j].startsWith("url")) {
                        tokens[j] = tokens[j].substring(tokens[j].indexOf("\""),
                                tokens[j].length() - 2);
                        currentPaper.setUrl(tokens[j]);
                    } else if (tokens[j].startsWith("abstract")) {
                        tokens[j] = tokens[j].substring(tokens[j].indexOf("\""),
                                tokens[j].length() - 2);
                        currentPaper.setAbsContent(tokens[j]);
                    }
                }
            }
            j++;
        }
        if (queryNum% threadsize != 0) {
            synchronized (threads[queryNum% threadsize]) {
                if (threads[queryNum% threadsize - 1].isAlive()) {
                    try {
                        threads[queryNum% threadsize].wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        paperBase.put(paperID, currentPaper);
        if (!currentPaper.getAuthors().isEmpty()) {
            for (String researcherName : currentPaper.getAuthors()) {
                Researcher existingResearch = null;
                for (Researcher researcher : researchers) {
                    if (researcher.getName().equals(researcherName)) {
                        existingResearch = researcher;
                        break;
                    }
                }
                if (existingResearch == null) {
                    Researcher researcher = new Researcher("Researcher_" + researchers.size(), researcherName);
                    researcher.appendNewPaper(currentPaper);
                    researchers.add(researcher);
                } else {
                    existingResearch.appendNewPaper(currentPaper);
                }
            }
        }
        if (queryNum % threadsize +1 != threadsize) {
            synchronized (threads[queryNum% threadsize + 1]) {
                threads[queryNum% threadsize + 1].notify();
                System.out.println("Count: " + queryNum + "Thread-" + (queryNum % threadsize)
                        + " notifying Thread-" + (queryNum % threadsize + 1)  + action.getQueries()
                        .get(queryNum).getQuery());
            }
        }
        action.getQueries().get(queryNum).setCompleted(true);
        action.getQueries().get(queryNum).setCompletedDate();
    }

    public void processUpdateQuery(QueryAction action, int queryNum, int threadsize, Thread[] threads){
        boolean exist = false;
        Query.Target object = action.getQueries().get(queryNum).getObject();
        String condition = action.getQueries().get(queryNum).getCondition();
        String newValue = action.getQueries().get(queryNum).getValue();
        if (queryNum% threadsize != 0) {
            synchronized (threads[queryNum% threadsize]) {
                if (threads[queryNum% threadsize - 1].isAlive()) {
                    try {
                        threads[queryNum% threadsize].wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        switch (object) {
            case TITLE:
                for (String paperID : paperBase.keySet()) {
                    if (paperBase.get(paperID).getTitle().equals(condition)) {
                        paperBase.get(paperID).setTitle(newValue);
                        exist = true;
                    }
                }
                break;

            case YEAR:
                for (String paperID : paperBase.keySet()) {
                    if (paperBase.get(paperID).getYear() == Integer.parseInt(condition)) {
                        paperBase.get(paperID).setYear(Integer.parseInt(newValue));
                        exist = true;
                    }
                }
                break;

            case JOURNAL:
                for (String paperID : paperBase.keySet()) {
                    if (paperBase.get(paperID).getJournal().equals(condition)) {
                        paperBase.get(paperID).setJournal(newValue);
                        exist = true;
                    }
                }
                break;

            case AUTHOR:
                for (String paperID : paperBase.keySet()) {
                    if (paperBase.get(paperID).getAuthors().contains(newValue)) {
                        for (int j = 0; j < paperBase.get(paperID).getAuthors().size(); j++) {
                            if (paperBase.get(paperID).getAuthors().get(j).equals(condition)) {
                                paperBase.get(paperID).getAuthors().remove(j);
                                for (int k = 0; k < researchers.size(); k++) {
                                    if (researchers.get(k).getName().equals(condition)) {
                                        researchers.remove(k);
                                        break;
                                    }
                                }
                                exist = true;
                                break;
                            }
                        }
                    } else {
                        for (int j = 0; j < paperBase.get(paperID).getAuthors().size(); j++) {
                            if (paperBase.get(paperID).getAuthors().get(j).equals(condition)) {
                                paperBase.get(paperID).getAuthors().set(j, newValue);
                                for (int k = 0; k < researchers.size(); k++) {
                                    if (researchers.get(k).getName().equals(condition)) {
                                        researchers.get(k).setName(newValue);
                                        break;
                                    }
                                }
                                exist = true;
                            }
                        }
                    }
                }
                break;

            case KEYWORDS:
                for (String paperID : paperBase.keySet()) {
                    if (paperBase.get(paperID).getKeywords().contains(newValue)) {
                        for (int j = 0; j < paperBase.get(paperID).getKeywords().size(); j++) {
                            if (paperBase.get(paperID).getKeywords().get(j).equals(condition)) {
                                paperBase.get(paperID).getKeywords().remove(j);
                                exist = true;
                                break;
                            }
                        }
                    } else {
                        for (int j = 0; j < paperBase.get(paperID).getKeywords().size(); j++) {
                            if (paperBase.get(paperID).getKeywords().get(j).equals(condition)) {
                                paperBase.get(paperID).getKeywords().set(j, newValue);
                                exist = true;
                            }
                        }
                    }
                }
                break;

            case null, default:
                break;
        }
        if (queryNum % threadsize +1 != threadsize) {
            synchronized (threads[queryNum% threadsize + 1]) {
                threads[queryNum% threadsize + 1].notify();
                System.out.println("Count: "+ queryNum +"Thread-"+(queryNum% threadsize)
                        +" notifying Thread-"+ (queryNum% threadsize + 1)  + action.getQueries()
                        .get(queryNum).getQuery());
            }
        }
        if (exist) {
            System.out.println(action.getQueries().get(queryNum).getQuery());
            action.getQueries().get(queryNum).setCompleted(true);
            action.getQueries().get(queryNum).setCompletedDate();
        }

    }

    public void processRemoveQuery(QueryAction action, int queryNum, int threadsize, Thread[] threads){
        boolean exist = false;
        Query.Target object = action.getQueries().get(queryNum).getObject();
        String condition = action.getQueries().get(queryNum).getCondition();
        if (queryNum% threadsize != 0) {
            synchronized (threads[queryNum% threadsize]) {
                if (threads[queryNum% threadsize - 1].isAlive()) {
                    try {
                        threads[queryNum% threadsize].wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        switch (object) {
            case TITLE:
                for (String paperID : paperBase.keySet()) {
                    if (paperBase.get(paperID).getTitle().equals(condition)) {
                        paperBase.get(paperID).setTitle(null);
                        exist = true;
                    }
                }
                break;

            case YEAR:
                for (String paperID : paperBase.keySet()) {
                    if (paperBase.get(paperID).getYear() == Integer.parseInt(condition)) {
                        paperBase.get(paperID).setYear(-1);
                        exist = true;
                    }
                }
                break;

            case JOURNAL:
                for (String paperID : paperBase.keySet()) {
                    if (paperBase.get(paperID).getJournal().equals(condition)) {
                        paperBase.get(paperID).setJournal(null);
                        exist = true;
                    }
                }
                break;

            case AUTHOR:
                for (String paperID : paperBase.keySet()) {
                    for (int j = 0; j < paperBase.get(paperID).getAuthors().size(); j++) {
                        if (paperBase.get(paperID).getAuthors().get(j).equals(condition)) {
                            paperBase.get(paperID).getAuthors().remove(j);
                            exist = true;
                            break;
                        }
                    }
                    for (int j = 0; j < researchers.size(); j++) {
                        if (researchers.get(j).getName().equals(condition)) {
                            researchers.remove(j);
                            exist = true;
                            break;
                        }
                    }
                }
                break;

            case KEYWORDS:
                for (String paperID : paperBase.keySet()) {
                    for (int j = 0; j < paperBase.get(paperID).getKeywords().size(); j++) {
                        if (paperBase.get(paperID).getKeywords().get(j).equals(condition)) {
                            paperBase.get(paperID).getKeywords().remove(j);
                            exist = true;
                            break;
                        }
                    }

                }
                break;

            case null, default:
                break;
        }
        if (queryNum % threadsize +1 != threadsize) {
            synchronized (threads[queryNum% threadsize + 1]) {
                threads[queryNum% threadsize + 1].notify();
                System.out.println("Count: "+ queryNum +"Thread-"+(queryNum% threadsize)
                        +" notifying Thread-"+ (queryNum% threadsize + 1)  + action.getQueries()
                        .get(queryNum).getQuery());
            }
        }
        if (exist) {
            System.out.println(action.getQueries().get(queryNum).getQuery());
            action.getQueries().get(queryNum).setCompleted(true);
            action.getQueries().get(queryNum).setCompletedDate();
        }
    }
}
