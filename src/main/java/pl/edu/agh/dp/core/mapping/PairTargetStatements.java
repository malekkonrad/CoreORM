package pl.edu.agh.dp.core.mapping;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PairTargetStatements {
    List<TargetStatement> whereStatements;
    List<TargetStatement> joinStatements;

    public PairTargetStatements() {
        this.whereStatements = new ArrayList<>(){{add(new TargetStatement("", null));}};
        this.joinStatements =  new ArrayList<>(){{add(new TargetStatement("", null));}};
    }

    public PairTargetStatements(TargetStatement whereStmt, TargetStatement joinStmt) {
        this.whereStatements = new ArrayList<>(){{add(whereStmt);}};
        this.joinStatements = new ArrayList<>(){{add(joinStmt);}};
    }

    public PairTargetStatements(List<TargetStatement> whereStmts, List<TargetStatement> joinStmts) {
        this.whereStatements = new ArrayList<>(whereStmts);
        this.joinStatements = new ArrayList<>(joinStmts);
    }

    public String unionAllStatement(List<String> statements, List<Object> params) {
        for (int i = 0; i < statements.size(); i++) {
            params.addAll(params);
        }
        return String.join(" UNION ALL ", statements);
    }

    public List<String> prepareStatements(String selectStatement, String whereStatement, String joinTableName, String whereTableName) {
        List<String> statements = new ArrayList<>();
        for (int i = 0; i < joinStatements.size(); i++) {
            String joinStmt = joinStatements.get(i).getStatement(joinTableName);
            String whereStmt = whereStatements.get(i).getStatement(whereTableName);
            StringBuilder sb = new StringBuilder();
            sb.append(selectStatement);

            sb.append(" ").append(joinStmt);

            if (!whereStatement.isBlank() && !whereStmt.isBlank()) {
                sb.append(" WHERE ");
                if (!whereStatement.isBlank()) {
                    sb.append(whereStatement);
                }
                if (!whereStmt.isBlank()) {
                    sb.append(whereStmt);
                }
            }
            statements.add(sb.toString());
        }
        return statements;
    }

    @Override
    public String toString() {
        return (
            "Where: " + whereStatements.toString() + "\n" +
            "Join: " + joinStatements.toString()
        );
    }
}
