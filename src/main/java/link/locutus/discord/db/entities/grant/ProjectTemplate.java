package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.offshore.Grant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ProjectTemplate extends AGrantTemplate{
    private final Project project;
    public ProjectTemplate(GuildDB db, int id, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, ResultSet rs) throws SQLException {
        super(db, id, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay);
        this.project = Projects.values[rs.getInt("project")];
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.PROJECT;
    }

    public Project getProject() {
        return project;
    }

    @Override
    public List<Grant.Requirement> getDefaultRequirements() {
        List<Grant.Requirement> list = super.getDefaultRequirements();
        // already got project grant
        // already have project
        // dont have requirements
        //

        return list;
    }
}