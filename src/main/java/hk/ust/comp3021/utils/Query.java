package hk.ust.comp3021.utils;

import java.util.Date;

public class Query {
    public enum QueryType{
        ADD,
        REMOVE,
        UPDATE,
    };
    public enum Target{
        PAPER,
        AUTHOR,
        JOURNAL,
        YEAR,
        KEYWORDS,
        TITLE,
    };
    private QueryType type;
    private Target object;
    private String value = "";
    private String query = "";
    private String condition = "";
    private boolean valid = false;
    private boolean completed = false;
    private Date completedDate;


    public Query(String query) {
        this.query = query;
        this.valid = processQuery(query);
    }

    /**
     * TODO Implement this function to check the validity of a query received from each line
     * @param query
     * @return
     */
    private boolean processQuery(String query) {
        String[] tokens = query.split(";");
        if(tokens.length > 0) {
            tokens[0] = tokens[0].trim();
            if (tokens[0].equals("ADD")) {
                setType(QueryType.ADD);
                if (tokens.length == 3 && tokens[1] != null && this.getTarget(tokens[1]) == Target.PAPER) {
                    setObject(Target.PAPER);
                    if (tokens[2] != null) {
                        setValue(tokens[2]);
                        return true;
                    }
                }
            } else if (tokens[0].equals("UPDATE")) {
                setType(QueryType.UPDATE);
                if (tokens.length == 4 && tokens[1] != null && this.getTarget(tokens[1]) != null) {
                    setObject(getTarget(tokens[1]));
                    if (tokens[2] != null && tokens[3] != null && !tokens[2].equals(tokens[3])) {
                        setCondition(tokens[2]);
                        setValue(tokens[3]);
                        return true;
                    }
                }
            } else if (tokens[0].equals("REMOVE")) {
                setType(QueryType.REMOVE);
                if (tokens.length == 3 && tokens[1] != null && this.getTarget(tokens[1]) != null) {
                    setObject(getTarget(tokens[1]));
                    if (tokens[2] != null) {
                        setCondition(tokens[2]);
                        return true;
                    }
                }
            }
        }
        return false;
    }
    /**
     * TODO Implement this function to find out which part of a bib format of a paper must be modified
     * @param target
     * @return
     */
    private Target getTarget(String target) {
        if(target.equals("PAPER")){
            return Target.PAPER;
        } else if (target.equals("AUTHOR")) {
            return Target.AUTHOR;
        }else if (target.equals("JOURNAL")) {
            return Target.JOURNAL;
        }else if (target.equals("YEAR")) {
            return Target.YEAR;
        }else if (target.equals("KEYWORDS")) {
            return Target.KEYWORDS;
        }else if (target.equals("TITLE")) {
            return Target.TITLE;
        }
        return null;
    }

    public Target getObject() {
        return object;
    }

    public String getQuery() {
        return query;
    }

    public QueryType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public boolean getValidity() {
        return valid;
    }

    public void setObject(Target object) {
        this.object = object;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public void setType(QueryType type) {
        this.type = type;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
        this.setCompletedDate();
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompletedDate() {
        this.completedDate = new Date();
    }

    public Date getCompletedDate() {
        return completedDate;
    }
}
