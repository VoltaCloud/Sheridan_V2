package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.grant.AGrantTemplate;
import link.locutus.discord.db.entities.grant.BuildTemplate;
import link.locutus.discord.db.entities.grant.CityTemplate;
import link.locutus.discord.db.entities.grant.GrantTemplateManager;
import link.locutus.discord.db.entities.grant.InfraTemplate;
import link.locutus.discord.db.entities.grant.LandTemplate;
import link.locutus.discord.db.entities.grant.ProjectTemplate;
import link.locutus.discord.db.entities.grant.RawsTemplate;
import link.locutus.discord.db.entities.grant.TemplateTypes;
import link.locutus.discord.db.entities.grant.WarchestTemplate;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.Grant;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.offshore.TransferResult;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class GrantCommands {

    @Command(desc = "Grant cities to a set of nations", groups = {
            "Amount options",
            "Account options",
            "Note options",
            "Escrow",
            "Policy/Project cost reduction"
    })
    @RolePermission(Roles.ECON)
    @IsAlliance
    public String grantCity(
            @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, @Me User author,

            Set<DBNation> receivers,

            @Arg(value = "Number of cities to grant") @Range(min=1, max=100) int amount,
            @Switch("u") @Arg(value = "If buying up to a city count, instead of additional cities", group = 0) boolean upTo,
            @Arg(value = "Only send funds the receiver is lacking from the amount", group = 0) @Switch("m") boolean onlySendMissingFunds,

            @Arg(value = "The nation account to deduct from", group = 1) @Switch("n") DBNation depositsAccount,
            @Arg(value = "The alliance bank to send from\nDefaults to the offshore", group = 1) @Switch("a") DBAlliance useAllianceBank,
            @Arg(value = "The alliance account to deduct from\nAlliance must be registered to this guild\nDefaults to all the alliances of this guild", group = 1) @Switch("o") DBAlliance useOffshoreAccount,
            @Arg(value = "The tax account to deduct from", group = 1) @Switch("t") TaxBracket taxAccount,
            @Arg(value = "Deduct from the receiver's tax bracket account", group = 1) @Switch("ta") boolean existingTaxAccount,

            @Arg(value = "Have the transfer ignored from nation holdings after a timeframe", group = 2) @Switch("e") @Timediff Long expire,
            @Arg(value = "Have the transfer decrease linearly from balances over a timeframe", group = 2) @Switch("d") @Timediff Long decay,
            @Arg(value = "Have the transfer not deduct from balance", group = 2) @Switch("i") boolean ignore,
            @Arg(value = "Have the transfer valued as cash in nation holdings", group = 2)@Switch("c") boolean convertToMoney,
            @Arg(value = "The mode for escrowing funds (e.g. if the receiver is blockaded)\nDefaults to never", group = 3) @Switch("em") EscrowMode escrow_mode,

            @Arg(value = "Apply the specified domestic policy for determining cost", group = 4) @Switch("md") Boolean manifest_destiny,
            @Arg(value = "Apply the specified project for determining cost", group = 4) @Switch("up") Boolean urban_planning,
            @Arg(value = "Apply the specified project for determining cost", group = 4) @Switch("aup") Boolean advanced_urban_planning,
            @Arg(value = "Apply the specified project for determining cost", group = 4) @Switch("mp") Boolean metropolitan_planning,
            @Arg(value = "Apply the specified project for determining cost", group = 4) @Switch("gsa") Boolean gov_support_agency,

            @Switch("b") boolean bypass_checks,
            @Switch("f") boolean force
    ) throws IOException, GeneralSecurityException {
        Function<DBNation, Integer> getNumBuy = receiver -> {
            int currentCity = receiver.getCities();
            return Math.max(upTo ? amount - currentCity : amount, 0);
        };
        return Grant.generateCommandLogic(io, db, me, author, receivers, onlySendMissingFunds, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, expire, decay, ignore, convertToMoney, escrow_mode, bypass_checks, force,
                (receiver, grant) -> {
                    int currentCity = receiver.getCities();
                    int numBuy = getNumBuy.apply(receiver);
                    if (numBuy <= 0) {
                        return new TransferResult(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, receiver, new HashMap<>(), "Nation already has " + amount + " cities");
                    }
                    DepositType.DepositTypeInfo note = DepositType.CITY.withAmount(currentCity + numBuy);
                    double cost = PW.City.cityCost(currentCity, currentCity + numBuy, manifest_destiny != null ? manifest_destiny : receiver.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY,
                            urban_planning != null ? urban_planning : receiver.hasProject(Projects.URBAN_PLANNING),
                            advanced_urban_planning != null ? advanced_urban_planning : receiver.hasProject(Projects.ADVANCED_URBAN_PLANNING),
                            metropolitan_planning != null ? metropolitan_planning : receiver.hasProject(Projects.METROPOLITAN_PLANNING),
                            gov_support_agency != null ? gov_support_agency : receiver.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY));
                    double[] resources = ResourceType.MONEY.toArray(cost);
                    grant.setInstructions("Go to <" + Settings.INSTANCE.PNW_URL() + "/city/create/> and purchase " + numBuy + " cities");
                    grant.setCost(f -> resources).setType(note);
                    return null;
                }, DepositType.CITY, receiver -> {
                    int numBuy = getNumBuy.apply(receiver);
                    return CityTemplate.getRequirements(db, me, receiver, null, numBuy);
                });
    }

    // Standard grant commands

    @Command(desc = "Grant a project to a set of nations", groups = {
            "Project options",
            "Account options",
            "Note options",
            "Escrow",
            "Policy/Project cost reduction"
    })
    @RolePermission(Roles.ECON)
    @IsAlliance
    public String grantProject(
            @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, @Me User author,
            Set<DBNation> receivers,
            Project project,
            @Arg(value = "Only send funds the receiver is lacking from the amount", group = 0) @Switch("m") boolean onlySendMissingFunds, 

            @Arg(value = "The nation account to deduct from", group = 1) @Switch("n") DBNation depositsAccount,
            @Arg(value = "The alliance bank to send from\nDefaults to the offshore", group = 1) @Switch("a") DBAlliance useAllianceBank,
            @Arg(value = "The alliance account to deduct from\nAlliance must be registered to this guild\nDefaults to all the alliances of this guild", group = 1) @Switch("o") DBAlliance useOffshoreAccount,
            @Arg(value = "The tax account to deduct from", group = 1) @Switch("t") TaxBracket taxAccount,
            @Arg(value = "Deduct from the receiver's tax bracket account", group = 1) @Switch("ta") boolean existingTaxAccount,
            
            @Arg(value = "Have the transfer ignored from nation holdings after a timeframe", group = 2) @Switch("e") @Timediff Long expire,
            @Arg(value = "Have the transfer decrease linearly from balances over a timeframe", group = 2) @Switch("d") @Timediff Long decay,
            @Arg(value = "Have the transfer not deduct from balance", group = 2) @Switch("i") boolean ignore, 
            @Arg(value = "Have the transfer valued as cash in nation holdings", group = 2) @Switch("c") boolean convertToMoney,

            @Arg(value = "The mode for escrowing funds (e.g. if the receiver is blockaded)\nDefaults to never", group = 3) @Switch("em") EscrowMode escrow_mode,

            @Arg(value = "Apply the specified domestic policy for determining cost", group = 4) @Switch("dpta") Boolean technological_advancement,
            @Arg(value = "Apply the specified project for determining cost", group = 4) @Switch("gsa") Boolean gov_support_agency,

            @Switch("b") boolean bypass_checks,
            @Switch("f") boolean force
    ) throws IOException, GeneralSecurityException {
        return Grant.generateCommandLogic(io, db, me, author, receivers, onlySendMissingFunds, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, expire, decay, ignore, convertToMoney, escrow_mode, bypass_checks, force,
            (receiver, grant) -> {
                if (receiver.hasProject(project)) {
                    return new TransferResult(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, receiver, new HashMap<>(), "Nation already has project: " + project.name());
                }
                boolean ta = technological_advancement != null ? technological_advancement : receiver.getDomesticPolicy() == DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT;
                boolean gsa = gov_support_agency != null ? gov_support_agency : receiver.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY);
                double[] cost = project.cost(ta, gsa);
                grant.setCost(f -> cost).setType(DepositType.PROJECT.withAmount(project.ordinal()));
                grant.setInstructions("Go to <" + Settings.INSTANCE.PNW_URL() + "/nation/projects/> and purchase `" + project.name() + "`");
                return null;
            }, DepositType.PROJECT, receiver -> {
                return ProjectTemplate.getRequirementsProject(db, me, receiver, null, project);
            });
    }

    // infra
    @Command(desc = "Grant infra to a set of nations", groups = {
            "Infra options",
            "Account options",
            "Note options",
            "Escrow",
            "Policy/Project cost reduction"
    })
    @RolePermission(Roles.ECON)
    @IsAlliance
    public String grantInfra(
            @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, @Me User author,
            Set<DBNation> receivers,

            @Range(min=50, max=10000) int infra_level,

            @Switch("new") @Arg(value = "If the grant is for a new city", group = 0) boolean single_new_city,
            @Arg(value = "Only send funds the receiver is lacking from the amount", group = 0) @Switch("m") boolean onlySendMissingFunds,

            @Arg(value = "The nation account to deduct from", group = 1) @Switch("n") DBNation depositsAccount,
            @Arg(value = "The alliance bank to send from\nDefaults to the offshore", group = 1) @Switch("a") DBAlliance useAllianceBank,
            @Arg(value = "The alliance account to deduct from\nAlliance must be registered to this guild\nDefaults to all the alliances of this guild", group = 1) @Switch("o") DBAlliance useOffshoreAccount,
            @Arg(value = "The tax account to deduct from", group = 1) @Switch("t") TaxBracket taxAccount,
            @Arg(value = "Deduct from the receiver's tax bracket account", group = 1) @Switch("ta") boolean existingTaxAccount,

            @Arg(value = "Have the transfer ignored from nation holdings after a timeframe", group = 2) @Switch("e") @Timediff Long expire,
            @Arg(value = "Have the transfer decrease linearly from balances over a timeframe", group = 2) @Switch("d") @Timediff Long decay,
            @Arg(value = "Have the transfer not deduct from balance", group = 2) @Switch("i") boolean ignore, 
            @Arg(value = "Have the transfer valued as cash in nation holdings", group = 2) @Switch("c") boolean convertToMoney,

            @Arg(value = "The mode for escrowing funds (e.g. if the receiver is blockaded)\nDefaults to never", group = 3) @Switch("em") EscrowMode escrow_mode,

            @Arg(value = "Apply the specified domestic policy for determining cost", group = 4) @Switch("u") Boolean urbanization,
            @Arg(value = "Apply the specified project for determining cost", group = 4) @Switch("aec") Boolean advanced_engineering_corps,
            @Arg(value = "Apply the specified project for determining cost", group = 4) @Switch("cfce") Boolean center_for_civil_engineering,
            @Arg(value = "Apply the specified project for determining cost", group = 4) @Switch("gsa") Boolean gov_support_agency,

            @Switch("b") boolean bypass_checks,
            @Switch("f") boolean force

    ) throws IOException, GeneralSecurityException {
        return Grant.generateCommandLogic(io, db, me, author, receivers, onlySendMissingFunds, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, expire, decay, ignore, convertToMoney, escrow_mode, bypass_checks, force,
            (receiver, grant) -> {
                double cost;
                if (single_new_city) {
                    cost = PW.City.Infra.calculateInfra(10, infra_level,
                            advanced_engineering_corps != null ? advanced_engineering_corps : false,
                            center_for_civil_engineering != null ? center_for_civil_engineering : false,
                            urbanization != null ? urbanization : false,
                            gov_support_agency != null ? gov_support_agency : false
                            );
                } else {
                    cost = receiver.getBuyInfraCost(infra_level,
                            urbanization != null ? urbanization : false,
                            advanced_engineering_corps != null ? advanced_engineering_corps : false,
                            center_for_civil_engineering != null ? center_for_civil_engineering : false,
                            gov_support_agency != null ? gov_support_agency : false);
                }
                if (cost <= 0) {
                    return new TransferResult(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, receiver, new HashMap<>(), "Nation already has infra level: " + infra_level);
                }
                if (single_new_city) {
                    grant.setInstructions("Go to your NEW city from <" + Settings.INSTANCE.PNW_URL() + "/cities/> and enter `@" + infra_level + "` infra. Use the `@` symbol to buy UP TO an amount");
                } else {
                    grant.setInstructions("Go to EACH city from <" + Settings.INSTANCE.PNW_URL() + "/cities/> and enter `@" + infra_level + "` infra. Use the `@` symbol to buy UP TO an amount");
                }
                grant.setCost(f -> ResourceType.MONEY.toArray(cost)).setType(DepositType.INFRA.withValue(infra_level, single_new_city ? 1 : receiver.getCities()));
                return null;
            }, DepositType.INFRA, receiver -> {
                return InfraTemplate.getRequirements(db, me, receiver, null, (double) infra_level);
            });
    }

    // land
    @Command(desc = "Grant land to a set of nations", groups = {
            "Amount options",
            "Account options",
            "Note options",
            "Escrow",
            "Policy/Project cost reduction"
    })
    @RolePermission(Roles.ECON)
    @IsAlliance
    public String grantLand(
            @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, @Me User author,
            Set<DBNation> receivers,
            @Range(min=1, max=10000) int to_land,

            @Switch("new") @Arg(value = "If the grant is for a new city", group = 0) boolean single_new_city,
            @Arg(value = "Only send funds the receiver is lacking from the amount", group = 0) @Switch("m") boolean onlySendMissingFunds,

            @Arg(value = "The nation account to deduct from", group = 1) @Switch("n") DBNation depositsAccount,
            @Arg(value = "The alliance bank to send from\nDefaults to the offshore", group = 1) @Switch("a") DBAlliance useAllianceBank,
            @Arg(value = "The alliance account to deduct from\nAlliance must be registered to this guild\nDefaults to all the alliances of this guild", group = 1) @Switch("o") DBAlliance useOffshoreAccount,
            @Arg(value = "The tax account to deduct from", group = 1) @Switch("t") TaxBracket taxAccount,
            @Arg(value = "Deduct from the receiver's tax bracket account", group = 1) @Switch("ta") boolean existingTaxAccount,
            @Arg(value = "Have the transfer ignored from nation holdings after a timeframe", group = 2) @Switch("e") @Timediff Long expire,
            @Arg(value = "Have the transfer decrease linearly from balances over a timeframe", group = 2) @Switch("d") @Timediff Long decay,
            @Arg(value = "Have the transfer not deduct from balance", group = 2) @Switch("i") boolean ignore, 
            @Arg(value = "Have the transfer valued as cash in nation holdings", group = 2)@Switch("c") boolean convertToMoney,

            @Arg(value = "The mode for escrowing funds (e.g. if the receiver is blockaded)\nDefaults to never", group = 3) @Switch("em") EscrowMode escrow_mode,

            @Arg(value = "Apply the specified domestic policy for determining cost", group = 4) @Switch("ra") Boolean rapid_expansion,
            @Arg(value = "Apply the specified project for determining cost", group = 4) @Switch("aec") Boolean advanced_engineering_corps,
            @Arg(value = "Apply the specified project for determining cost", group = 4) @Switch("ala") Boolean arable_land_agency,
            @Arg(value = "Apply the specified project for determining cost", group = 4) @Switch("gsa") Boolean gov_support_agency,

            @Switch("b") boolean bypass_checks,
            @Switch("f") boolean force
    ) throws IOException, GeneralSecurityException {
        return Grant.generateCommandLogic(io, db, me, author, receivers, onlySendMissingFunds, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, expire, decay, ignore, convertToMoney, escrow_mode, bypass_checks, force,
                (receiver, grant) -> {
                    double cost = receiver.getBuyLandCost(to_land,
                            rapid_expansion != null ? rapid_expansion : false,
                            advanced_engineering_corps != null ? advanced_engineering_corps : false,
                            arable_land_agency != null ? arable_land_agency : false,
                            gov_support_agency != null ? gov_support_agency : false);
                    if (cost <= 0) {
                        return new TransferResult(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, receiver, new HashMap<>(), "Nation already has " + to_land + " land");
                    }
                    if (single_new_city) {
                        grant.setInstructions("Go to your NEW city from <" + Settings.INSTANCE.PNW_URL() + "/cities/> and enter `@" + to_land + "` land. Use the `@` symbol to buy UP TO an amount");
                    } else {
                        grant.setInstructions("Go to EACH city from <" + Settings.INSTANCE.PNW_URL() + "/cities/> and enter `@" + to_land + "` land. Use the `@` symbol to buy UP TO an amount");
                    }
                    grant.setCost(f -> ResourceType.MONEY.toArray(cost)).setType(DepositType.LAND.withValue(to_land, single_new_city ? 1 : receiver.getCities()));
                    return null;
                }, DepositType.LAND, receiver -> {
                    return LandTemplate.getRequirements(db, me, receiver, null, (double) to_land);
                });
    }

    // unit
    @Command(desc = "Grant units to a set of nations", groups = {
            "Unit options",
            "Account options",
            "Note options",
            "Escrow"
    })
    @RolePermission(Roles.ECON)
    @IsAlliance
    public String grantUnit(
            @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, @Me User author,
            Set<DBNation> receivers,
            Map<MilitaryUnit, Long> units,

            @Arg(value = "Multiple the units specified by the receivers cities", group = 0) boolean scale_per_city,
            @Arg(value = "Only send funds for units the receiver is lacking", group = 0) boolean only_missing_units,
            @Arg(value = "Only send funds the receiver is lacking from the amount", group = 0) @Switch("m") boolean onlySendMissingFunds,

            @Arg(value = "The nation account to deduct from", group = 1) @Switch("n") DBNation depositsAccount,
            @Arg(value = "The alliance bank to send from\nDefaults to the offshore", group = 1) @Switch("a") DBAlliance useAllianceBank,
            @Arg(value = "The alliance account to deduct from\nAlliance must be registered to this guild\nDefaults to all the alliances of this guild", group = 1) @Switch("o") DBAlliance useOffshoreAccount,
            @Arg(value = "The tax account to deduct from", group = 1) @Switch("t") TaxBracket taxAccount,
            @Arg(value = "Deduct from the receiver's tax bracket account", group = 1) @Switch("ta") boolean existingTaxAccount,
            @Arg(value = "Have the transfer ignored from nation holdings after a timeframe", group = 2) @Switch("e") @Timediff Long expire,
            @Arg(value = "Have the transfer decrease linearly from balances over a timeframe", group = 2) @Switch("d") @Timediff Long decay,
            @Arg(value = "Have the transfer not deduct from balance", group = 2) @Switch("i") boolean ignore, 
            @Arg(value = "Have the transfer valued as cash in nation holdings", group = 2)@Switch("c") boolean convertToMoney,

            @Arg(value = "The mode for escrowing funds (e.g. if the receiver is blockaded)\nDefaults to never", group = 3) @Switch("em") EscrowMode escrow_mode,

            @Switch("b") boolean bypass_checks,
            @Switch("f") boolean force
    ) throws IOException, GeneralSecurityException {
        return Grant.generateCommandLogic(io, db, me, author, receivers, onlySendMissingFunds, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, expire, decay, ignore, convertToMoney, escrow_mode, bypass_checks, force,
            (receiver, grant) -> {
                Map<MilitaryUnit, Long> unitsToGrant = new HashMap<>();
                units.forEach((unit, amount) -> {
                    long scaledAmount = scale_per_city ? amount * receiver.getCities() : amount;
                    long current = receiver.getUnits(unit);
                    long finalAmount = only_missing_units ? Math.max(scaledAmount - current, 0) : scaledAmount;
                    if (finalAmount > 0) {
                        unitsToGrant.put(unit, finalAmount);
                    }
                });
                if (unitsToGrant.isEmpty()) {
                    return new TransferResult(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, receiver, new HashMap<>(), "Nation already has the units");
                }
                ResourceType.ResourcesBuilder cost = ResourceType.builder();
                unitsToGrant.forEach((unit, amount) -> {
                    cost.add(unit.getCost(amount.intValue()));
                });
                grant.setInstructions("Go to <" + Settings.INSTANCE.PNW_URL() + "/nation/military/> and purchase `" + unitsToGrant + "`");
                grant.setCost(f -> cost.build()).setType(DepositType.WARCHEST.withValue());
                return null;
            },
            DepositType.WARCHEST, receiver -> {
                return null;
            });
    }

    // mmr
    @Command(desc = "Grant units equivalent to an MMR value to a set of nations", groups = {
            "MMR options",
            "Account options",
            "Note options",
            "Escrow",
    })
    @RolePermission(Roles.ECON)
    @IsAlliance
    public String grantMMR(
            @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, @Me User author,
            Set<DBNation> receivers,
            MMRDouble mmr,

            @Arg(value = "If the mmr being granted is for new units, rather than only the difference from current units", group = 0) @Switch("u") boolean is_additional_units,
            @Arg(value = "Only send funds the receiver is lacking from the amount", group = 0) @Switch("m") boolean onlySendMissingFunds, 

            @Arg(value = "The nation account to deduct from", group = 1) @Switch("n") DBNation depositsAccount,
            @Arg(value = "The alliance bank to send from\nDefaults to the offshore", group = 1) @Switch("a") DBAlliance useAllianceBank,
            @Arg(value = "The alliance account to deduct from\nAlliance must be registered to this guild\nDefaults to all the alliances of this guild", group = 1) @Switch("o") DBAlliance useOffshoreAccount,
            @Arg(value = "The tax account to deduct from", group = 1) @Switch("t") TaxBracket taxAccount,
            @Arg(value = "Deduct from the receiver's tax bracket account", group = 1) @Switch("ta") boolean existingTaxAccount,
            @Arg(value = "Have the transfer ignored from nation holdings after a timeframe", group = 2) @Switch("e") @Timediff Long expire,
            @Arg(value = "Have the transfer decrease linearly from balances over a timeframe", group = 2) @Switch("d") @Timediff Long decay,
            @Arg(value = "Have the transfer not deduct from balance", group = 2) @Switch("i") boolean ignore, 
            @Arg(value = "Have the transfer valued as cash in nation holdings", group = 2) @Switch("c") boolean convertToMoney,
            @Arg(value = "The mode for escrowing funds (e.g. if the receiver is blockaded)\nDefaults to never", group = 3) @Switch("em") EscrowMode escrow_mode,

            @Switch("b") boolean bypass_checks,
            @Switch("f") boolean force
    ) throws IOException, GeneralSecurityException {
        return Grant.generateCommandLogic(io, db, me, author, receivers, onlySendMissingFunds, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, expire, decay, ignore, convertToMoney, escrow_mode, bypass_checks, force,
            (receiver, grant) -> {
                int cities = receiver.getCities();
                Map<MilitaryUnit, Integer> unitsToGrant = new HashMap<>();
                for (Building building : Buildings.MILITARY_BUILDINGS) {
                    MilitaryBuilding militaryBuilding = (MilitaryBuilding) building;
                    MilitaryUnit unit = militaryBuilding.getMilitaryUnit();
                    double pctGrant = mmr.getPercent(unit);
                    int unitCap = militaryBuilding.cap(receiver::hasProject) * militaryBuilding.getUnitCap() * cities;
                    double currPct = receiver.getUnits(unit) / (double) unitCap;
                    if (!is_additional_units) {
                        pctGrant -= currPct;
                    }
                    int numUnits = (int) Math.ceil(pctGrant * unitCap);
                    if (numUnits > 0) unitsToGrant.put(unit, numUnits);
                }
                if (unitsToGrant.isEmpty()) {
                    return new TransferResult(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, receiver, new HashMap<>(), "Nation already has the units");
                }
                ResourceType.ResourcesBuilder cost = ResourceType.builder();
                unitsToGrant.forEach((unit, amount) -> cost.add(unit.getCost(amount)));
                grant.setInstructions("Go to <" + Settings.INSTANCE.PNW_URL() + "/nation/military/> and purchase `" + unitsToGrant + "`");
                grant.setCost(f -> cost.build()).setType(DepositType.WARCHEST.withValue());
                return null;
            }, DepositType.WARCHEST, receiver -> {
                return null;
            });
    }

    @Command(desc = "Grant consumptions resources for a specified number of attacks\n" +
            "Consumption assumes at max MMR (military units) for the receivers city count", groups = {
            "Number of Attacks",
            "Send Amount Modes",
            "Account options",
            "Note options",
            "Escrow"
    })
    @RolePermission(Roles.ECON)
    @IsAlliance
    public String grantConsumption(
            @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, @Me User author,
            Set<DBNation> receivers,

            @Arg(value = "Number of Soldier attacks with munitions", group = 0) @Range(min=0, max=10000) Integer soldier_attacks,
            @Arg(value = "Number of Tank attacks", group = 0) @Range(min=0, max=10000) Integer tank_attacks,
            @Arg(value = "Number of Airstrikes", group = 0) @Range(min=0, max=10000) Integer airstrikes,
            @Arg(value = "Number of Naval Attacks", group = 0) @Range(min=0, max=10000) Integer naval_attacks,
            @Arg(value = "Number of Missiles", group = 0) @Range(min=0, max=10000) @Default Integer missiles,
            @Arg(value = "Number of Nukes", group = 0) @Range(min=0, max=10000) @Default Integer nukes,

            @Arg(value = "Attach a bonus percent, to account for loot losses", group = 1) @Switch("p") Integer bonus_percent,
            @Arg(value = "Only send funds the receiver is lacking from the amount", group = 1) @Switch("m") boolean onlySendMissingFunds,

            @Arg(value = "The nation account to deduct from", group = 2) @Switch("n") DBNation depositsAccount,
            @Arg(value = "The alliance bank to send from\nDefaults to the offshore", group = 2) @Switch("a") DBAlliance useAllianceBank,
            @Arg(value = "The alliance account to deduct from\nAlliance must be registered to this guild\nDefaults to all the alliances of this guild", group = 2) @Switch("o") DBAlliance useOffshoreAccount,
            @Arg(value = "The tax account to deduct from", group = 2) @Switch("t") TaxBracket taxAccount,
            @Arg(value = "Deduct from the receiver's tax bracket account", group = 2) @Switch("ta") boolean existingTaxAccount,
            @Arg(value = "Have the transfer ignored from nation holdings after a timeframe", group = 3) @Switch("e") @Timediff Long expire,
            @Arg(value = "Have the transfer decrease linearly from balances over a timeframe", group = 3) @Switch("d") @Timediff Long decay,
            @Arg(value = "Have the transfer not deduct from balance", group = 3) @Switch("i") boolean ignore,
            @Arg(value = "Have the transfer valued as cash in nation holdings", group = 3)@Switch("c") boolean convertToMoney,
            @Arg(value = "The mode for escrowing funds (e.g. if the receiver is blockaded)\nDefaults to never", group = 4) @Switch("em") EscrowMode escrow_mode,

            @Switch("b") boolean bypass_checks,
            @Switch("f") boolean force
    ) throws IOException, GeneralSecurityException {
        return Grant.generateCommandLogic(io, db, me, author, receivers, onlySendMissingFunds, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, expire, decay, ignore, convertToMoney, escrow_mode, bypass_checks, force,
                (receiver, grant) -> {
                    int cities = receiver.getCities();
                    Map<MilitaryUnit, Integer> numAttacks = new LinkedHashMap<>(Map.of(
                            MilitaryUnit.SOLDIER, soldier_attacks,
                            MilitaryUnit.TANK, tank_attacks,
                            MilitaryUnit.AIRCRAFT, airstrikes,
                            MilitaryUnit.SHIP, naval_attacks,
                            MilitaryUnit.MISSILE, missiles == null ? 0 : missiles,
                            MilitaryUnit.NUKE, nukes == null ? 0 : nukes
                    ));
                    numAttacks.entrySet().removeIf(e -> e.getValue() <= 0);
                    ResourceType.ResourcesBuilder cost = ResourceType.builder();
                    numAttacks.forEach((unit, amount) -> {
                        if (amount <= 0) return;
                        if (unit == MilitaryUnit.MISSILE || unit == MilitaryUnit.NUKE) {
                            cost.add(unit.getCost(amount));
                            return;
                        }
                        int maxUnits = unit.getMaxMMRCap(cities, receiver::hasProject);
                        cost.add(PW.multiply(unit.getConsumption(), amount * maxUnits));
                    });
                    if (cost.isEmpty()) {
                        return new TransferResult(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, receiver, new HashMap<>(), "No attacks specified");
                    }
                    double[] costArr = cost.build();;
                    if (bonus_percent != null && bonus_percent != 0) {
                        double factor = 1 + bonus_percent * 0.01;
                        PW.multiply(costArr, factor);
                    }
                    grant.setInstructions("You have been granted the resources for the following number of attacks:\n`" + numAttacks + "`");
                    grant.setCost(f -> costArr).setType(DepositType.WARCHEST.withValue());
                    return null;
                }, DepositType.WARCHEST, receiver -> {
                    return null;
                });
    }
    // build

    @Command(desc = "Grant consumptions resources for a specified number of attacks at max MMR (military units)",
    groups = {
            "Specify Cities",
            "Send Modes (Infra/Land/Bonus/Missing)",
            "Account options",
            "Note options",
            "Escrow"
    })
    @RolePermission(Roles.ECON)
    @IsAlliance
    public String grantBuild(
            @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, @Me User author,
            Set<DBNation> receivers,
            CityBuild build,

            @Arg(value = "Grant only for a single new city", group = 0) boolean is_new_city,
            @Arg(value = "Grant for a specific city ids", group = 0) Set<Integer> city_ids,

            @Arg(value = "Send funds for infrastructure (if specified in build)\nDefault: False", group = 1) @Switch("infra") Boolean grant_infra,
            @Arg(value = "Send funds for land (if specified in build)\nDefault: False", group = 1) @Switch("land") Boolean grant_land,
            @Arg(value = "Attach a bonus percent, to account for loot losses", group = 1) @Switch("p") Integer bonus_percent,
            @Arg(value = "Only send funds the receiver is lacking from the amount", group = 1) @Switch("m") boolean onlySendMissingFunds,

            @Arg(value = "The nation account to deduct from", group = 2) @Switch("n") DBNation depositsAccount,
            @Arg(value = "The alliance bank to send from\nDefaults to the offshore", group = 2) @Switch("a") DBAlliance useAllianceBank,
            @Arg(value = "The alliance account to deduct from\nAlliance must be registered to this guild\nDefaults to all the alliances of this guild", group = 2) @Switch("o") DBAlliance useOffshoreAccount,
            @Arg(value = "The tax account to deduct from", group = 2) @Switch("t") TaxBracket taxAccount,
            @Arg(value = "Deduct from the receiver's tax bracket account", group = 2) @Switch("ta") boolean existingTaxAccount,
            @Arg(value = "Have the transfer ignored from nation holdings after a timeframe", group = 3) @Switch("e") @Timediff Long expire,
            @Arg(value = "Have the transfer decrease linearly from balances over a timeframe", group = 3) @Switch("d") @Timediff Long decay,
            @Arg(value = "Have the transfer not deduct from balance", group = 3) @Switch("i") boolean ignore,
            @Arg(value = "Have the transfer valued as cash in nation holdings", group = 3) @Switch("c") boolean convertToMoney,

            @Arg(value = "The mode for escrowing funds (e.g. if the receiver is blockaded)\nDefaults to never", group = 4) @Switch("em") EscrowMode escrow_mode,
            @Switch("b") boolean bypass_checks,
            @Switch("f") boolean force
    ) throws IOException, GeneralSecurityException {
        if (city_ids != null) {
            if (receivers.size() > 1) {
                throw new IllegalArgumentException("Cannot specify `city_ids` and multiple receivers (max 1)");
            }
            if (is_new_city) {
                throw new IllegalArgumentException("Cannot specify both `is_new_city` and `city_ids`");
            }
        }
        List<String> notes = new ArrayList<>();
        if (build.getAge() == null) {
            notes.add("Specify the age of the build using `age: 1234` in the build json");
        }
        if (build.getLand() == null) {
            notes.add("Specify the land of the build using `land: 1234` in the build json");
        }
        if (!notes.isEmpty()) {
            notes.add("You can append partial city json to a city url to modify an existing build");
        }
        return Grant.generateCommandLogic(io, db, me, author, receivers, onlySendMissingFunds, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, expire, decay, ignore, convertToMoney, escrow_mode, bypass_checks, force,
                (receiver, grant) -> {
                    JavaCity grantTo = new JavaCity(build);
                    try {
                        grantTo.canBuild(receiver.getContinent(), receiver::hasProject, true);
                    } catch (IllegalArgumentException e) {
                        return new TransferResult(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, receiver, new HashMap<>(), e.getMessage());
                    }
                    boolean grantLand = build.getLand() != null && (grant_land != null ? grant_land : false);
                    boolean grantInfra = (grant_infra != null ? grant_infra : false);

                    double[] cost = ResourceType.getBuffer();
                    int citiesGranted;
                    if (is_new_city) {
                        citiesGranted = 1;
                        JavaCity empty = new JavaCity();
                        empty.setLand(grantLand ? 250d : grantTo.getLand());
                        empty.setInfra(grantInfra ? 10d : grantTo.getInfra());
                        cost = grantTo.calculateCost(empty);
                        grant.setInstructions(grantTo.instructions(-1, empty, ResourceType.getBuffer()));
                    } else {
                        boolean hasDiffBuildings = true;
                        Map<Integer, JavaCity> grantFrom = new LinkedHashMap<>();
                        for (Map.Entry<Integer, JavaCity> entry : receiver.getCityMap(receivers.size() == 1).entrySet()) {
                            JavaCity city = entry.getValue();
                            if (city_ids != null && !city_ids.contains(entry.getKey())) {
                                continue;
                            }
                            if (city.equals(grantTo)) {
                                continue;
                            }
                            hasDiffBuildings = false;
                            double[] buffer = grantTo.calculateCost(city, ResourceType.getBuffer(), grantInfra, grantLand);
                            ResourceType.add(cost, buffer);
                            for (ResourceType type : ResourceType.values) {
                                cost[type.ordinal()] = Math.max(0, cost[type.ordinal()]);
                            }
                            grantFrom.put(entry.getKey(), city);
                        }
                        if (!hasDiffBuildings) {
                            return new TransferResult(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, receiver, new HashMap<>(), "Nation already has the build");
                        }
                        grant.setInstructions(grantTo.instructions(grantFrom, ResourceType.getBuffer(), city_ids == null, true));
                        citiesGranted = grantFrom.size();
                    }
                    if (ResourceType.isZero(cost)) {
                        return new TransferResult(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, receiver, new HashMap<>(), "No resources are needed to import this build");
                    }
                    if (bonus_percent != null && bonus_percent != 0) {
                        double factor = 1 + bonus_percent * 0.01;
                        cost = PW.multiply(cost, factor);
                    }
                    double[] finalCost = cost;
                    long pair = MathMan.pairInt(grantInfra ? (int) grantTo.getInfra() : 0, grantLand ? (int) grantTo.getLand() : 0);
                    grant.setCost(f -> finalCost).setType(DepositType.BUILD.withValue(pair, citiesGranted));
                    return null;
                }, DepositType.BUILD, receiver -> {
                    return BuildTemplate.getRequirements(db, me, receiver, null, Map.of(-1, build));
                });
    }

    @Command(desc = "Grant a multiple of the warchest requirements to a set of nations\n" +
            "Use 1 for the default warchest\n" +
            "If no warchest is configured, a default will be used, see setting `WARCHEST_PER_CITY`", groups = {
            "Amount option",
            "Account options",
            "Note options",
            "Escrow"
    })
    @RolePermission(Roles.ECON)
    @IsAlliance
    public String grantWarchest(
            @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, @Me User author,
            Set<DBNation> receivers,
            @Range(min=0.01, max=50) double ratio,

            @Arg(value = "Only send funds the receiver is lacking from the amount", group = 0) @Switch("m") boolean onlySendMissingFunds, 
            @Arg(value = "The nation account to deduct from", group = 1) @Switch("n") DBNation depositsAccount,
            @Arg(value = "The alliance bank to send from\nDefaults to the offshore", group = 1) @Switch("a") DBAlliance useAllianceBank,
            @Arg(value = "The alliance account to deduct from\nAlliance must be registered to this guild\nDefaults to all the alliances of this guild", group = 1) @Switch("o") DBAlliance useOffshoreAccount,
            @Arg(value = "The tax account to deduct from", group = 1) @Switch("t") TaxBracket taxAccount,
            @Arg(value = "Deduct from the receiver's tax bracket account", group = 1) @Switch("ta") boolean existingTaxAccount,
            @Arg(value = "Have the transfer ignored from nation holdings after a timeframe", group = 2) @Switch("e") @Timediff Long expire,
            @Arg(value = "Have the transfer decrease linearly from balances over a timeframe", group = 2) @Switch("d") @Timediff Long decay,
            @Arg(value = "Have the transfer not deduct from balance", group = 2) @Switch("i") boolean ignore, 
            @Arg(value = "Have the transfer valued as cash in nation holdings", group = 2) @Switch("c") boolean convertToMoney,

            @Arg(value = "The mode for escrowing funds (e.g. if the receiver is blockaded)\nDefaults to never", group = 3) @Switch("em") EscrowMode escrow_mode,
            @Switch("b") boolean bypass_checks,
            @Switch("f") boolean force
    ) throws IOException, GeneralSecurityException {
        return Grant.generateCommandLogic(io, db, me, author, receivers, onlySendMissingFunds, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, expire, decay, ignore, convertToMoney, escrow_mode, bypass_checks, force,
            (receiver, grant) -> {
                int cities = receiver.getCities();
                Map<ResourceType, Double> perCity = db.getPerCityWarchest(receiver);
                Map<ResourceType, Double> wc = PW.multiply(perCity, (double) cities * ratio);
                grant.setCost(f -> ResourceType.resourcesToArray(wc)).setType(DepositType.WARCHEST.withValue());
                return null;
            }, DepositType.WARCHEST, receiver -> {
                return null;
            });
    }

    // Template commands

    @Command(desc = "List all grant templates for the specified category")
    @RolePermission(Roles.MEMBER)
    public void templateList(@Me GuildDB db, @Me Guild guild, @Me JSONObject command, @Me User author, @Me Member member, @Me DBNation me, @Me IMessageIO io,
                             @Arg("The category of templates to list\n" +
                                     "Defaults to all categories")@Default TemplateTypes category,
                             @Arg("List the disabled grant templates") @Switch("d") boolean listDisabled) {
        GrantTemplateManager manager = db.getGrantTemplateManager();
        Set<AGrantTemplate> templates = new HashSet<>(category == null ? manager.getTemplates() : manager.getTemplates(category));
        int numDisabled = templates.stream().mapToInt(t -> t.isEnabled() ? 0 : 1).sum();
        if (!listDisabled) {
            templates.removeIf(t -> !t.isEnabled());
        }
        if (templates.isEmpty()) {
            String body;
            if (category == null) {
                body = "No templates found for all categories";
            } else {
                body = "No templates found for category: " + category + "\n" +
                        "Create one with " + category.getCommandMention();
            }
            if (!listDisabled && numDisabled > 0) {
                body += "\nUse `listDisabled` to list `" + numDisabled + "` disabled templates";
            }
            IMessageBuilder msg = io.create().embed("No Templates Found", body);
            if (!listDisabled && numDisabled > 0) {
                command.put("listDisabled", "true");
                msg = msg.commandButton(command, "View Disabled");
            }
            msg.send();
            return;
        }

        boolean hasAdmin = Roles.ADMIN.has(author, guild);

        List<AGrantTemplate> grantOthers = new ArrayList<>();
        List<AGrantTemplate> grantSelf = new ArrayList<>();
        List<AGrantTemplate> disabled = new ArrayList<>();
        List<AGrantTemplate> noAccess = new ArrayList<>();

        for (AGrantTemplate template : templates) {
            if (template.isEnabled() && (hasAdmin || template.hasRole(member))) {
                grantOthers.add(template);
            } else if (template.isEnabled() && template.hasSelfRole(member)) {
                grantSelf.add(template);
            } else if (!template.isEnabled() && (template.hasRole(member) || template.hasSelfRole(member))) {
                disabled.add(template);
            } else {
                noAccess.add(template);
            }
        }

        StringBuilder result = new StringBuilder();
        if (!grantOthers.isEmpty()) {
            result.append("### Grant Others:\n");
            for (AGrantTemplate template : grantOthers) {
                result.append("- ").append(template.toListString()).append("\n");
            }
        }
        if (!grantSelf.isEmpty()) {
            result.append("### Grant Self:\n");
            for (AGrantTemplate template : grantSelf) {
                result.append("- ").append(template.toListString()).append("\n");
            }
        }
        if (!disabled.isEmpty()) {
            result.append("### Disabled:\n");
            for (AGrantTemplate template : disabled) {
                result.append("- ").append(template.toListString()).append("\n");
            }
        }
        if (!noAccess.isEmpty()) {
            result.append("### No Access:\n");
            for (AGrantTemplate template : noAccess) {
                result.append("- ").append(template.toListString()).append("\n");
            }
        }

        if (numDisabled > 0 && !listDisabled) {
            result.append("\n`" + numDisabled + "` disabled templates\n" +
            "Use `listDisabled: True` to list them");
        }

        io.send(result.toString());
    }

    @Command(desc = "Full information about a grant template")
    @RolePermission(Roles.MEMBER)
    public String templateInfo(@Me GuildDB db, @Me JSONObject command, @Me Guild guild, @Me User author, @Me Member member, @Me DBNation me, @Me IMessageIO io, AGrantTemplate template,
                               @Arg("View additional info related to granting the template to this nation\n" +
                                       "Such as cost/eligability")
                               @Default DBNation receiver,
                               @Arg("The value to provide to the grant template\n" +
                                       "Such as:\n" +
                                       "- Number (infra, land, grant, city, raws)\n" +
                                       "- City build json (build)\n" +
                                       "- Resources (warchest)")
                               @Default String value,
                               @Arg("Show the command used to create this template\n" +
                                       "i.e. If you want to copy or recreate the template")
                               @Switch("e") boolean show_command) {
        if (receiver == null) receiver = me;
        if (show_command) {
            return "### Edit Command\n`" + template.getCommandString() + "`";
        }
        JSONObject editJson = command.put("show_command", "true");
        io.create().embed(template.getName(), template.toFullString(me, receiver, value))
                .commandButton(editJson, "Edit").send();
        return null;
    }

    // grant_template Delete
    @Command(desc = "Delete a grant template")
    @RolePermission(Roles.ECON)
    public String templateDelete(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command, AGrantTemplate template, @Switch("f") boolean force) {
        if (!force) {
            String body = template.toFullString(me, null, null);
            io.create().confirmation("Delete template: " + template.getName(), body.toString(), command).send();
            return null;
        }
        db.getGrantTemplateManager().deleteTemplate(template);
        return "The template: `" + template.getName() + "` has been deleted.";
    }

    // grant_template disable
    @Command(desc = "Set an active grant template as disabled")
    @RolePermission(Roles.ECON)
    public String templateDisable(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command, AGrantTemplate template, @Switch("f") boolean force) {
        if (!template.isEnabled()) {
            return "The template: `" + template.getName() + "` is already disabled.";
        }
        template.setEnabled(false);
        db.getGrantTemplateManager().saveTemplate(template);
        return "The template: `" + template.getName() + "` has been disabled.";
    }

    @Command(desc = "Set a disabled grant template as enabled")
    @RolePermission(Roles.ECON)
    public String templateEnabled(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, AGrantTemplate template) {
        if (template.isEnabled()) {
            return "The template: `" + template.getName() + "` is already enabled.";
        }
        template.setEnabled(true);
        db.getGrantTemplateManager().saveTemplate(template);
        return "The template: `" + template.getName() + "` has been enabled.";
    }

    // grant_template create project
    // public ProjectTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, Project project) {
    @Command(desc = "Create a new project grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateProject(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                        @Arg("The name of the template\n" +
                                                "Alphanumerical") String name,
                                        @Arg("A filter for nations allowed to receive this grant\n" +
                                                "Use your alliance link for all nations\n" +
                                                "See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>")
                                        NationFilter allowedRecipients,
                                        @Arg("The project to grant")
                                        Project project,
                                        @Arg("The role that can grant this template to others\n" +
                                                "Defaults to the ECON role (see `{prefix}role setalias`)")
                                        @Switch("e") Role econRole,
                                        @Arg("The role that can grant this template to itself\n" +
                                                "Defaults to disabled")
                                        @Switch("s") Role selfRole,
                                        @Arg("The tax bracket account to use for withdrawals\n" +
                                                "e.g. For a growth circle\n" +
                                                "Defaults to None\n" +
                                                "See: <https://github.com/xdnw/locutus/wiki/tax_automation#tax-bracket-accounts>")
                                        @Switch("b")TaxBracket bracket,
                                        @Arg("If the receiver's tax bracket is used as the tax bracket account\n" +
                                                "Defaults to false\n" +
                                                "Alternative to `bracket`")
                                        @Switch("r") boolean useReceiverBracket,
                                        @Arg("Global grants allowed for this template\n" +
                                                "Defaults to unlimited")
                                        @Switch("mt") Integer maxTotal,
                                        @Arg("Grants allowed for this template per day\n" +
                                                "Defaults to unlimited")
                                        @Switch("md") Integer maxDay,
                                        @Arg("Grants allowed for this template per day by the same sender\n" +
                                                "Defaults to unlimited")
                                        @Switch("mgd") Integer maxGranterDay,
                                        @Arg("Grants allowed for this template by the same sender\n" +
                                                "Defaults to unlimited")
                                        @Switch("mgt") Integer maxGranterTotal,
                                        @Arg("Add a default expiry time to grants sent via this template\n" +
                                                "e.g. 60d\n" +
                                                "The granter can specify an expiry shorter than this value")
                                        @Switch("expire") @Timediff Long expireTime,
                                        @Arg("Add a default decaying expiry time to grants sent via this template\n" +
                                                "e.g. 60d\n" +
                                                "The granter can specify an expiry shorter than this value")
                                            @Switch("decay") @Timediff Long decayTime,
                                        @Arg("Do not include grants in member balances by default\n" +
                                                "Defaults to false")
                                        @Switch("ignore") boolean allowIgnore,
                                        @Switch("f") boolean force) {
        System.out.println(1);
        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        System.out.println(2);
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }
        System.out.println(3);
        ProjectTemplate template = new ProjectTemplate(db, false, name, allowedRecipients, econRole.getIdLong(), selfRole.getIdLong(), bracket == null ? 0 : bracket.getId(), useReceiverBracket, maxTotal == null ? 0 : maxTotal, maxDay == null ? 0 : maxDay, maxGranterDay == null ? 0 : maxGranterDay, maxGranterTotal == null ? 0 : maxGranterTotal, System.currentTimeMillis(), project, expireTime == null ? 0 : expireTime, decayTime == null ? 0 : decayTime, allowIgnore);
        System.out.println(4);
        AGrantTemplate existing = manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)).stream().findFirst().orElse(null);
        System.out.println(5);
        if (existing != null && existing.getType() != template.getType()) {
            throw new IllegalArgumentException("A template with that name already exists of type `" + existing.getType() + "`. See: " + CM.grant_template.delete.cmd.toSlashMention());
        }
        // confirmation
        if (!force) {
            System.out.println(6);
            String body = template.toFullString(me, null, null);
            System.out.println(7);
            Set<Integer> aaIds = db.getAllianceIds();
            System.out.println(7.1);
            Set<DBNation> nations = Locutus.imp().getNationDB().getNationsMatching(allowedRecipients.toCached(Long.MAX_VALUE));
            System.out.println(7.2);
            nations.removeIf(f -> !aaIds.contains(f.getAlliance_id()));
            System.out.println(8);
            if (nations.isEmpty()) {
                body = "**WARNING: NO NATIONS MATCHING `" + allowedRecipients.getFilter() + "`**\n\n" + body;
            }
            if (existing != null) {
                body = "**OVERWRITE EXISTING TEMPLATE**\n\n" +
                        "View the existing template: " + CM.grant_template.info.cmd.toSlashMention() +
                        "\n\n" + body;
            }
            System.out.println(9);
            String prefix = existing != null ? "Overwrite " : "Create ";
            io.create().confirmation(prefix + "Template: " + template.getName(), body, command).send();
            System.out.println(10);
            return null;
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. Templates must be enabled to be used. See:\n" +
                "- " + CM.grant_template.enable.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.delete.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.send.cmd.toSlashMention();
    }

    // grant_template create build
    // public BuildTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, byte[] build, boolean only_new_cities, long mmr, long track_days, boolean allow_switch_after_offensive) {
    @Command(desc = "Create a new build grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateBuild(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                      @Arg("The name of the template\n" +
                                              "Alphanumerical") String name,
                                      @Arg("A filter for nations allowed to receive this grant\n" +
                                              "Use your alliance link for all nations\n" +
                                              "See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>")
                                          NationFilter allowedRecipients,
                                      @Arg("Only grant this specific city build\n" +
                                              "Defaults to allow any city build")
                                      @Switch("c") CityBuild build,
                                      @Arg("The MMR required for grants via this template")
                                      @Switch("m") MMRInt mmr,
                                      @Arg("Only allow granting builds for new cities\n" +
                                              "(Past 10 days)")
                                      @Switch("o") boolean only_new_cities,
                                      @Arg("Allow grants to cities that have not received a build in the past X days\n" +
                                              "Defaults to no limit")
                                      @Switch("t") Integer allow_after_days,
                                      @Arg("Allow sending to cities after the receiver has had an offensive war\n" +
                                              "e.g. For for switching builds after a counter\n" +
                                              "Defaults to False")
                                      @Switch("a") boolean allow_after_offensive,
                                      @Arg("Allow sending to cities where infrastructure has been damaged\n" +
                                              "Defaults to False")
                                      @Switch("i") boolean allow_after_infra,
                                      @Arg("Always allow granting (even if they have received another city build grant)")
                                      @Switch("aa") boolean allow_all,
                                      @Arg("Allow granting after purchasing land or a project\n" +
                                              "Defaults to False")
                                      @Switch("lp") boolean allow_after_land_or_project,
                                      @Arg("The role that can grant this template to others\n" +
                                              "Defaults to the ECON role (see `{prefix}role setalias`)")
                                          @Switch("e") Role econRole,
                                      @Arg("The role that can grant this template to itself\n" +
                                              "Defaults to disabled")
                                          @Switch("s") Role selfRole,
                                      @Arg("The tax bracket account to use for withdrawals\n" +
                                              "e.g. For a growth circle\n" +
                                              "Defaults to None\n" +
                                              "See: <https://github.com/xdnw/locutus/wiki/tax_automation#tax-bracket-accounts>")
                                          @Switch("b")TaxBracket bracket,
                                      @Arg("If the receiver's tax bracket is used as the tax bracket account\n" +
                                              "Defaults to false\n" +
                                              "Alternative to `bracket`")
                                          @Switch("r") boolean useReceiverBracket,
                                      @Arg("Global grants allowed for this template\n" +
                                              "Defaults to unlimited")
                                          @Switch("mt") Integer maxTotal,
                                      @Arg("Grants allowed for this template per day\n" +
                                              "Defaults to unlimited")
                                          @Switch("md") Integer maxDay,
                                      @Arg("Grants allowed for this template per day by the same sender\n" +
                                              "Defaults to unlimited")
                                          @Switch("mgd") Integer maxGranterDay,
                                      @Arg("Grants allowed for this template by the same sender\n" +
                                              "Defaults to unlimited")
                                          @Switch("mgt") Integer maxGranterTotal,
                                      @Arg("Add a default expiry time to grants sent via this template\n" +
                                              "e.g. 60d\n" +
                                              "The granter can specify an expiry shorter than this value")
                                          @Switch("expire") @Timediff Long expireTime,
                                        @Arg("Add a default decaying expiry time to grants sent via this template\n" +
                                                "e.g. 60d\n" +
                                                "The granter can specify an expiry shorter than this value")
                                            @Switch("decay") @Timediff Long decayTime,
                                      @Arg("Do not include grants in member balances by default\n" +
                                              "Defaults to false")
                                          @Switch("ignore") boolean allowIgnore,
                                      @Arg("If the template can be sent to the same receiver multiple times")
                                          @Switch("repeat") @Timediff Long repeatable_time,
                                      @Switch("f") boolean force) {

        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }
        byte[] buildBytes = build == null ? null : new JavaCity(build).toBytes();

        BuildTemplate template = new BuildTemplate(db, false, name, allowedRecipients, econRole.getIdLong(), selfRole.getIdLong(), bracket == null ? 0 : bracket.getId(), useReceiverBracket, maxTotal == null ? 0 : maxTotal, maxDay == null ? 0 : maxDay, maxGranterDay == null ? 0 : maxGranterDay, maxGranterTotal == null ? 0 : maxGranterTotal, System.currentTimeMillis(), buildBytes, only_new_cities, mmr == null ? -1 : mmr.toNumber(),
                allow_after_days == null ? 0 : allow_after_days,
                allow_after_offensive, allow_after_infra, allow_after_land_or_project, allow_all, expireTime == null ? 0 : expireTime, decayTime == null ? 0 : decayTime, allowIgnore, repeatable_time == null ? -1 : repeatable_time);
        AGrantTemplate existing = manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)).stream().findFirst().orElse(null);
        if (existing != null && existing.getType() != template.getType()) {
            throw new IllegalArgumentException("A template with that name already exists of type `" + existing.getType() + "`. See: " + CM.grant_template.delete.cmd.toSlashMention());
        }
        // confirmation
        if (!force) {
            String body = template.toFullString(me, null, null);
            Set<Integer> aaIds = db.getAllianceIds();
            Set<DBNation> nations = Locutus.imp().getNationDB().getNationsMatching(allowedRecipients.toCached(Long.MAX_VALUE));
            nations.removeIf(f -> !aaIds.contains(f.getAlliance_id()));
            if (nations.isEmpty()) {
                body = "**WARNING: NO NATIONS MATCHING `" + allowedRecipients.getFilter() + "`**\n\n" + body;
            }
            if (existing != null) {
                body = "**OVERWRITE EXISTING TEMPLATE**\n\n" +
                        "View the existing template: " + CM.grant_template.info.cmd.toSlashMention() +
                        "\n\n" + body;
            }
            String prefix = existing != null ? "Overwrite " : "Create ";
            io.create().confirmation(prefix + "Template: " + template.getName(), body, command).send();
            return null;
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. Templates must be enabled to be used. See:\n" +
                "- " + CM.grant_template.enable.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.delete.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.send.cmd.toSlashMention();
    }

    // grant_template create city
    // public CityTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, int min_city, int max_city) {
    @Command(desc = "Create a new city grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateCity(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                     @Arg("The name of the template\n" +
                                             "Alphanumerical") String name,
                                     @Arg("A filter for nations allowed to receive this grant\n" +
                                             "Use your alliance link for all nations\n" +
                                             "See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>")
                                         NationFilter allowedRecipients,
                                     @Arg("The minimum city range allowed to receive grants")
                                     Integer minCity,
                                     @Arg("The maximum city allowed to grant up to")
                                     Integer maxCity,
                                     @Arg("The role that can grant this template to others\n" +
                                             "Defaults to the ECON role (see `{prefix}role setalias`)")
                                         @Switch("e") Role econRole,
                                     @Arg("The role that can grant this template to itself\n" +
                                             "Defaults to disabled")
                                         @Switch("s") Role selfRole,
                                     @Arg("The tax bracket account to use for withdrawals\n" +
                                             "e.g. For a growth circle\n" +
                                             "Defaults to None\n" +
                                             "See: <https://github.com/xdnw/locutus/wiki/tax_automation#tax-bracket-accounts>")
                                         @Switch("b")TaxBracket bracket,
                                     @Arg("If the receiver's tax bracket is used as the tax bracket account\n" +
                                             "Defaults to false\n" +
                                             "Alternative to `bracket`")
                                         @Switch("r") boolean useReceiverBracket,
                                     @Arg("Global grants allowed for this template\n" +
                                             "Defaults to unlimited")
                                         @Switch("mt") Integer maxTotal,
                                     @Arg("Grants allowed for this template per day\n" +
                                             "Defaults to unlimited")
                                         @Switch("md") Integer maxDay,
                                     @Arg("Grants allowed for this template per day by the same sender\n" +
                                             "Defaults to unlimited")
                                         @Switch("mgd") Integer maxGranterDay,
                                     @Arg("Grants allowed for this template by the same sender\n" +
                                             "Defaults to unlimited")
                                         @Switch("mgt") Integer maxGranterTotal,
                                     @Arg("Add a default expiry time to grants sent via this template\n" +
                                             "e.g. 60d\n" +
                                             "The granter can specify an expiry shorter than this value")
                                         @Switch("expire") @Timediff Long expireTime,
                                        @Arg("Add a default decaying expiry time to grants sent via this template\n" +
                                                "e.g. 60d\n" +
                                                "The granter can specify an expiry shorter than this value")
                                            @Switch("decay") @Timediff Long decayTime,
                                     @Arg("Do not include grants in member balances by default\n" +
                                             "Defaults to false")
                                         @Switch("ignore") boolean allowIgnore,
                                     @Switch("f") boolean force) {
        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }

        CityTemplate template = new CityTemplate(db, false, name, allowedRecipients, econRole.getIdLong(), selfRole.getIdLong(), bracket == null ? 0 : bracket.getId(), useReceiverBracket, maxTotal == null ? 0 : maxTotal, maxDay == null ? 0 : maxDay, maxGranterDay == null ? 0 : maxGranterDay, maxGranterTotal == null ? 0 : maxGranterTotal, System.currentTimeMillis(), minCity == null ? 0 : minCity, maxCity == null ? 0 : maxCity, expireTime == null ? 0 : expireTime, decayTime == null ? 0 : decayTime, allowIgnore);
        AGrantTemplate existing = manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)).stream().findFirst().orElse(null);
        if (existing != null && existing.getType() != template.getType()) {
            throw new IllegalArgumentException("A template with that name already exists of type `" + existing.getType() + "`. See: " + CM.grant_template.delete.cmd.toSlashMention());
        }
        // confirmation
        if (!force) {
            String body = template.toFullString(me, null, null);
            Set<Integer> aaIds = db.getAllianceIds();
            Set<DBNation> nations = Locutus.imp().getNationDB().getNationsMatching(allowedRecipients.toCached(Long.MAX_VALUE));
            nations.removeIf(f -> !aaIds.contains(f.getAlliance_id()));
            if (nations.isEmpty()) {
                body = "**WARNING: NO NATIONS MATCHING `" + allowedRecipients.getFilter() + "`**\n\n" + body;
            }
            if (existing != null) {
                body = "**OVERWRITE EXISTING TEMPLATE**\n\n" +
                        "View the existing template: " + CM.grant_template.info.cmd.toSlashMention() +
                        "\n\n" + body;
            }
            String prefix = existing != null ? "Overwrite " : "Create ";
            io.create().confirmation(prefix + "Template: " + template.getName(), body, command).send();
            return null;
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. Templates must be enabled to be used. See:\n" +
                "- " + CM.grant_template.enable.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.delete.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.send.cmd.toSlashMention();
    }

    // grant_template create infra
    // public InfraTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long level, boolean onlyNewCities, boolean track_days, long require_n_offensives, boolean allow_rebuild) {
    @Command(desc = "Create a new infra grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateInfra(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                      @Arg("The name of the template\n" +
                                              "Alphanumerical") String name,
                                      @Arg("A filter for nations allowed to receive this grant\n" +
                                              "Use your alliance link for all nations\n" +
                                              "See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>")
                                          NationFilter allowedRecipients,
                                      @Arg("The infra level allowed to grant to")
                                      Integer level,
                                      @Arg("Only allow grants to new cities (past 10 days)\n" +
                                              "Defaults to false")
                                      @Switch("n") boolean onlyNewCities,
                                      @Arg("Require N offensive wars before allowing infra grants")
                                      @Switch("o") Integer requireNOffensives,
                                      @Arg("Allow granting infra to cities that have received a grant in the past provided it has received damage")
                                      @Switch("a") boolean allowRebuild,
                                      @Arg("The role that can grant this template to others\n" +
                                              "Defaults to the ECON role (see `{prefix}role setalias`)")
                                          @Switch("e") Role econRole,
                                      @Arg("The role that can grant this template to itself\n" +
                                              "Defaults to disabled")
                                          @Switch("s") Role selfRole,
                                      @Arg("The tax bracket account to use for withdrawals\n" +
                                              "e.g. For a growth circle\n" +
                                              "Defaults to None\n" +
                                              "See: <https://github.com/xdnw/locutus/wiki/tax_automation#tax-bracket-accounts>")
                                          @Switch("b")TaxBracket bracket,
                                      @Arg("If the receiver's tax bracket is used as the tax bracket account\n" +
                                              "Defaults to false\n" +
                                              "Alternative to `bracket`")
                                          @Switch("r") boolean useReceiverBracket,
                                      @Arg("Global grants allowed for this template\n" +
                                              "Defaults to unlimited")
                                          @Switch("mt") Integer maxTotal,
                                      @Arg("Grants allowed for this template per day\n" +
                                              "Defaults to unlimited")
                                          @Switch("md") Integer maxDay,
                                      @Arg("Grants allowed for this template per day by the same sender\n" +
                                              "Defaults to unlimited")
                                          @Switch("mgd") Integer maxGranterDay,
                                      @Arg("Grants allowed for this template by the same sender\n" +
                                              "Defaults to unlimited")
                                          @Switch("mgt") Integer maxGranterTotal,
                                      @Arg("Add a default expiry time to grants sent via this template\n" +
                                              "e.g. 60d\n" +
                                              "The granter can specify an expiry shorter than this value")
                                          @Switch("expire") @Timediff Long expireTime,
                                        @Arg("Add a default decaying expiry time to grants sent via this template\n" +
                                                "e.g. 60d\n" +
                                                "The granter can specify an expiry shorter than this value")
                                            @Switch("decay") @Timediff Long decayTime,
                                      @Arg("Do not include grants in member balances by default\n" +
                                              "Defaults to false")
                                          @Switch("ignore") boolean allowIgnore,
                                      @Arg("If the template can be sent to the same receiver multiple times")
                                          @Switch("repeat") @Timediff Long repeatable_time,
                                      @Switch("f") boolean force) {
        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }

        InfraTemplate template = new InfraTemplate(db,
                false,
                name,
                allowedRecipients,
                econRole.getIdLong(),
                selfRole.getIdLong(),
                bracket == null ? 0 : bracket.getId(),
                useReceiverBracket,
                maxTotal == null ? 0 : maxTotal,
                maxDay == null ? 0 : maxDay,
                maxGranterDay == null ? 0 : maxGranterDay,
                maxGranterTotal == null ? 0 : maxGranterTotal,
                System.currentTimeMillis(),
                level,
                onlyNewCities,
                requireNOffensives == null ? 0 : requireNOffensives,
                allowRebuild, expireTime == null ? 0 : expireTime, decayTime == null ? 0 : decayTime, allowIgnore, repeatable_time == null ? -1 : repeatable_time);
        AGrantTemplate existing = manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)).stream().findFirst().orElse(null);
        if (existing != null && existing.getType() != template.getType()) {
            throw new IllegalArgumentException("A template with that name already exists of type `" + existing.getType() + "`. See: " + CM.grant_template.delete.cmd.toSlashMention());
        }
        // confirmation
        if (!force) {
            String body = template.toFullString(me, null, null);
            Set<Integer> aaIds = db.getAllianceIds();
            Set<DBNation> nations = Locutus.imp().getNationDB().getNationsMatching(allowedRecipients.toCached(Long.MAX_VALUE));
            nations.removeIf(f -> !aaIds.contains(f.getAlliance_id()));
            if (nations.isEmpty()) {
                body = "**WARNING: NO NATIONS MATCHING `" + allowedRecipients.getFilter() + "`**\n\n" + body;
            }
            if (existing != null) {
                body = "**OVERWRITE EXISTING TEMPLATE**\n\n" +
                        "View the existing template: " + CM.grant_template.info.cmd.toSlashMention() +
                        "\n\n" + body;
            }
            String prefix = existing != null ? "Overwrite " : "Create ";
            io.create().confirmation(prefix + "Template: " + template.getName(), body, command).send();
            return null;
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. Templates must be enabled to be used. See:\n" +
                "- " + CM.grant_template.enable.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.delete.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.send.cmd.toSlashMention();
    }

    // grant_template create land
    // public LandTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long level, boolean onlyNewCities) {
    @Command(desc = "Create a new land grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateLand(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                     @Arg("The name of the template\n" +
                                             "Alphanumerical") String name,
                                     @Arg("A filter for nations allowed to receive this grant\n" +
                                             "Use your alliance link for all nations\n" +
                                             "See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>")
                                         NationFilter allowedRecipients,
                                     @Arg("The land level to grant up to (inclusive)")
                                     Integer level,
                                     @Arg("Only allow grants to new cities (past 10 days)")
                                     @Switch("n") boolean onlyNewCities,
                                     @Arg("The role that can grant this template to others\n" +
                                             "Defaults to the ECON role (see `{prefix}role setalias`)")
                                         @Switch("e") Role econRole,
                                     @Arg("The role that can grant this template to itself\n" +
                                             "Defaults to disabled")
                                         @Switch("s") Role selfRole,
                                     @Arg("The tax bracket account to use for withdrawals\n" +
                                             "e.g. For a growth circle\n" +
                                             "Defaults to None\n" +
                                             "See: <https://github.com/xdnw/locutus/wiki/tax_automation#tax-bracket-accounts>")
                                         @Switch("b")TaxBracket bracket,
                                     @Arg("If the receiver's tax bracket is used as the tax bracket account\n" +
                                             "Defaults to false\n" +
                                             "Alternative to `bracket`")
                                         @Switch("r") boolean useReceiverBracket,
                                     @Arg("Global grants allowed for this template\n" +
                                             "Defaults to unlimited")
                                         @Switch("mt") Integer maxTotal,
                                     @Arg("Grants allowed for this template per day\n" +
                                             "Defaults to unlimited")
                                         @Switch("md") Integer maxDay,
                                     @Arg("Grants allowed for this template per day by the same sender\n" +
                                             "Defaults to unlimited")
                                         @Switch("mgd") Integer maxGranterDay,
                                     @Arg("Grants allowed for this template by the same sender\n" +
                                             "Defaults to unlimited")
                                         @Switch("mgt") Integer maxGranterTotal,
                                     @Arg("Add a default expiry time to grants sent via this template\n" +
                                             "e.g. 60d\n" +
                                             "The granter can specify an expiry shorter than this value")
                                         @Switch("expire") @Timediff Long expireTime,
                                        @Arg("Add a default decaying expiry time to grants sent via this template\n" +
                                                "e.g. 60d\n" +
                                                "The granter can specify an expiry shorter than this value")
                                            @Switch("decay") @Timediff Long decayTime,
                                     @Arg("Do not include grants in member balances by default\n" +
                                             "Defaults to false")
                                         @Switch("ignore") boolean allowIgnore,
                                     @Arg("If the template can be sent to the same receiver multiple times")
                                         @Switch("repeat") @Timediff Long repeatable_time,
                                     @Switch("f") boolean force) {
        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }

        LandTemplate template = new LandTemplate(db, false, name, allowedRecipients, econRole.getIdLong(), selfRole.getIdLong(), bracket == null ? 0 : bracket.getId(), useReceiverBracket, maxTotal == null ? 0 : maxTotal, maxDay == null ? 0 : maxDay, maxGranterDay == null ? 0 : maxGranterDay, maxGranterTotal == null ? 0 : maxGranterTotal, System.currentTimeMillis(), level == null ? 0 : level, onlyNewCities, expireTime == null ? 0 : expireTime, decayTime == null ? 0 : decayTime, allowIgnore, repeatable_time == null ? -1 : repeatable_time);
        AGrantTemplate existing = manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)).stream().findFirst().orElse(null);
        if (existing != null && existing.getType() != template.getType()) {
            throw new IllegalArgumentException("A template with that name already exists of type `" + existing.getType() + "`. See: " + CM.grant_template.delete.cmd.toSlashMention());
        }
        // confirmation
        if (!force) {
            String body = template.toFullString(me, null, null);
            Set<Integer> aaIds = db.getAllianceIds();
            Set<DBNation> nations = Locutus.imp().getNationDB().getNationsMatching(allowedRecipients.toCached(Long.MAX_VALUE));
            nations.removeIf(f -> !aaIds.contains(f.getAlliance_id()));
            if (nations.isEmpty()) {
                body = "**WARNING: NO NATIONS MATCHING `" + allowedRecipients.getFilter() + "`**\n\n" + body;
            }
            if (existing != null) {
                body = "**OVERWRITE EXISTING TEMPLATE**\n\n" +
                        "View the existing template: " + CM.grant_template.info.cmd.toSlashMention() +
                        "\n\n" + body;
            }
            String prefix = existing != null ? "Overwrite " : "Create ";
            io.create().confirmation(prefix + "Template: " + template.getName(), body, command).send();
            return null;
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. Templates must be enabled to be used. See:\n" +
                "- " + CM.grant_template.enable.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.delete.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.send.cmd.toSlashMention();
    }

    // grant_template create raws
    // public RawsTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long days, long overdrawPercentCents) {
    @Command(desc = "Create a new raws grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateRaws(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                     @Arg("The name of the template\n" +
                                             "Alphanumerical") String name,
                                     @Arg("A filter for nations allowed to receive this grant\n" +
                                             "Use your alliance link for all nations\n" +
                                             "See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>")
                                         NationFilter allowedRecipients,
                                     @Arg("Allow disbursing raw resources to run cities for up to a number of days")
                                     long days,
                                     @Arg("Allow oversupply of resources by a certain percent\n" +
                                             "Defaults to: 20 (percent)")
                                     @Switch("o") Long overdrawPercent,
                                     @Arg("The role that can grant this template to others\n" +
                                             "Defaults to the ECON role (see `{prefix}role setalias`)")
                                         @Switch("e") Role econRole,
                                     @Arg("The role that can grant this template to itself\n" +
                                             "Defaults to disabled")
                                         @Switch("s") Role selfRole,
                                     @Arg("The tax bracket account to use for withdrawals\n" +
                                             "e.g. For a growth circle\n" +
                                             "Defaults to None\n" +
                                             "See: <https://github.com/xdnw/locutus/wiki/tax_automation#tax-bracket-accounts>")
                                         @Switch("b")TaxBracket bracket,
                                     @Arg("If the receiver's tax bracket is used as the tax bracket account\n" +
                                             "Defaults to false\n" +
                                             "Alternative to `bracket`")
                                         @Switch("r") boolean useReceiverBracket,
                                     @Arg("Global grants allowed for this template\n" +
                                             "Defaults to unlimited")
                                         @Switch("mt") Integer maxTotal,
                                     @Arg("Grants allowed for this template per day\n" +
                                             "Defaults to unlimited")
                                         @Switch("md") Integer maxDay,
                                     @Arg("Grants allowed for this template per day by the same sender\n" +
                                             "Defaults to unlimited")
                                         @Switch("mgd") Integer maxGranterDay,
                                     @Arg("Grants allowed for this template by the same sender\n" +
                                             "Defaults to unlimited")
                                         @Switch("mgt") Integer maxGranterTotal,
                                     @Arg("Add a default expiry time to grants sent via this template\n" +
                                             "e.g. 60d\n" +
                                             "The granter can specify an expiry shorter than this value")
                                         @Switch("expire") @Timediff Long expireTime,
                                        @Arg("Add a default decaying expiry time to grants sent via this template\n" +
                                                "e.g. 60d\n" +
                                                "The granter can specify an expiry shorter than this value")
                                            @Switch("decay") @Timediff Long decayTime,
                                     @Arg("Do not include grants in member balances by default\n" +
                                             "Defaults to false")
                                         @Switch("ignore") boolean allowIgnore,
                                     @Arg("If the template can only sent to the same receiver once\n" +
                                             "Defaults to 1d")
                                         @Switch("repeat") @Timediff Long repeatable_time,
                                     @Switch("f") boolean force) {
        if (repeatable_time == null) repeatable_time = TimeUnit.DAYS.toMillis(1);
        if (overdrawPercent == null) overdrawPercent = 20L;
        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }

        RawsTemplate template = new RawsTemplate(db, false, name, allowedRecipients, econRole.getIdLong(), selfRole.getIdLong(), bracket == null ? 0 : bracket.getId(), useReceiverBracket, maxTotal == null ? 0 : maxTotal, maxDay == null ? 0 : maxDay, maxGranterDay == null ? 0 : maxGranterDay, maxGranterTotal == null ? 0 : maxGranterTotal, System.currentTimeMillis(), days, overdrawPercent, expireTime == null ? 0 : expireTime, decayTime == null ? 0 : decayTime, allowIgnore, repeatable_time == null ? -1 : repeatable_time);
        AGrantTemplate existing = manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)).stream().findFirst().orElse(null);
        if (existing != null && existing.getType() != template.getType()) {
            throw new IllegalArgumentException("A template with that name already exists of type `" + existing.getType() + "`. See: " + CM.grant_template.delete.cmd.toSlashMention());
        }
        // confirmation
        if (!force) {
            String body = template.toFullString(me, null, null);
            Set<Integer> aaIds = db.getAllianceIds();
            Set<DBNation> nations = Locutus.imp().getNationDB().getNationsMatching(allowedRecipients.toCached(Long.MAX_VALUE));
            nations.removeIf(f -> !aaIds.contains(f.getAlliance_id()));
            if (nations.isEmpty()) {
                body = "**WARNING: NO NATIONS MATCHING `" + allowedRecipients.getFilter() + "`**\n\n" + body;
            }
            if (existing != null) {
                body = "**OVERWRITE EXISTING TEMPLATE**\n\n" +
                        "View the existing template: " + CM.grant_template.info.cmd.toSlashMention() +
                        "\n\n" + body;
            }
            String prefix = existing != null ? "Overwrite " : "Create ";
            io.create().confirmation(prefix + "Template: " + template.getName(), body, command).send();
            return null;
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. Templates must be enabled to be used. See:\n" +
                "- " + CM.grant_template.enable.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.delete.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.send.cmd.toSlashMention();
    }

    // grant_template create warchest
    // public WarchestTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, double[] allowancePerCity, long trackDays, boolean subtractExpenditure, long overdrawPercentCents) {
    @Command(desc = "Create a new warchest grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateWarchest(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                         @Arg("The name of the template\n" +
                                                 "Alphanumerical") String name,
                                         @Arg("A filter for nations allowed to receive this grant\n" +
                                                 "Use your alliance link for all nations\n" +
                                                 "See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>")
                                             NationFilter allowedRecipients,
                                         @Arg("The warchest allowed to grant (per city)\n" +
                                                 "Defaults to the guild setting: `WARCHEST_PER_CITY`")
                                         @Switch("a") Map<ResourceType, Double> allowancePerCity,
                                         @Arg("Allow granting warchest if they have not received it in the past number of days")
                                         @Switch("t") Long trackDays,
                                         @Arg("Allow granting warchest that has been consumed in war")
                                         @Switch("c") boolean subtractExpenditure,
                                         @Arg("Allow granting a certain percent above the allowance to account for unintended losses (e.g. looting)\n" +
                                                 "Defaults to 0 (percent)")
                                         @Switch("o") Long overdrawPercent,
                                         @Arg("The role that can grant this template to others\n" +
                                                 "Defaults to the ECON role (see `{prefix}role setalias`)")
                                             @Switch("e") Role econRole,
                                         @Arg("The role that can grant this template to itself\n" +
                                                 "Defaults to disabled")
                                             @Switch("s") Role selfRole,
                                         @Arg("The tax bracket account to use for withdrawals\n" +
                                                 "e.g. For a growth circle\n" +
                                                 "Defaults to None\n" +
                                                 "See: <https://github.com/xdnw/locutus/wiki/tax_automation#tax-bracket-accounts>")
                                             @Switch("b")TaxBracket bracket,
                                         @Arg("If the receiver's tax bracket is used as the tax bracket account\n" +
                                                 "Defaults to false\n" +
                                                 "Alternative to `bracket`")
                                             @Switch("r") boolean useReceiverBracket,
                                         @Arg("Global grants allowed for this template\n" +
                                                 "Defaults to unlimited")
                                             @Switch("mt") Integer maxTotal,
                                         @Arg("Grants allowed for this template per day\n" +
                                                 "Defaults to unlimited")
                                             @Switch("md") Integer maxDay,
                                         @Arg("Grants allowed for this template per day by the same sender\n" +
                                                 "Defaults to unlimited")
                                             @Switch("mgd") Integer maxGranterDay,
                                         @Arg("Grants allowed for this template by the same sender\n" +
                                                 "Defaults to unlimited")
                                             @Switch("mgt") Integer maxGranterTotal,
                                         @Arg("Add a default expiry time to grants sent via this template\n" +
                                                 "e.g. 60d\n" +
                                                 "The granter can specify an expiry shorter than this value")
                                             @Switch("expire") @Timediff Long expireTime,
                                        @Arg("Add a default decaying expiry time to grants sent via this template\n" +
                                                "e.g. 60d\n" +
                                                "The granter can specify an expiry shorter than this value")
                                            @Switch("decay") @Timediff Long decayTime,
                                         @Arg("Do not include grants in member balances by default\n" +
                                                 "Defaults to false")
                                             @Switch("ignore") boolean allowIgnore,
                                         @Arg("If the template can be sent to the same receiver multiple times\n" +
                                                 "Defaults to 1d")
                                             @Switch("repeat") @Timediff Long repeatable_time,
                                         @Switch("f") boolean force) {
        if (repeatable_time == null) repeatable_time = TimeUnit.DAYS.toMillis(1);
        long overdrawPercentCents = overdrawPercent == null ? 0 : overdrawPercent * 100;
        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }

        double[] allowancePerCityArr = allowancePerCity == null ? null : ResourceType.resourcesToArray(allowancePerCity);
        WarchestTemplate template = new WarchestTemplate(db, false, name, allowedRecipients, econRole.getIdLong(), selfRole.getIdLong(), bracket == null ? 0 : bracket.getId(), useReceiverBracket, maxTotal == null ? 0 : maxTotal, maxDay == null ? 0 : maxDay, maxGranterDay == null ? 0 : maxGranterDay, maxGranterTotal == null ? 0 : maxGranterTotal, System.currentTimeMillis(), allowancePerCityArr, trackDays == null ? 0 : trackDays, subtractExpenditure, overdrawPercentCents, expireTime == null ? 0 : expireTime, decayTime == null ? 0 : decayTime, allowIgnore, repeatable_time == null ? -1 : repeatable_time);

        AGrantTemplate existing = manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)).stream().findFirst().orElse(null);
        if (existing != null && existing.getType() != template.getType()) {
            throw new IllegalArgumentException("A template with that name already exists of type `" + existing.getType() + "`. See: " + CM.grant_template.delete.cmd.toSlashMention());
        }

        // confirmation
        if (!force) {
            String body = template.toFullString(me, null, null);
            Set<Integer> aaIds = db.getAllianceIds();
            Set<DBNation> nations = Locutus.imp().getNationDB().getNationsMatching(allowedRecipients.toCached(Long.MAX_VALUE));
            nations.removeIf(f -> !aaIds.contains(f.getAlliance_id()));
            if (nations.isEmpty()) {
                body = "**WARNING: NO NATIONS MATCHING `" + allowedRecipients.getFilter() + "`**\n\n" + body;
            }
            if (existing != null) {
                body = "**OVERWRITE EXISTING TEMPLATE**\n\n" +
                        "View the existing template: " + CM.grant_template.info.cmd.toSlashMention() +
                        "\n\n" + body;
            }
            String prefix = existing != null ? "Overwrite " : "Create ";
            io.create().confirmation(prefix + "Template: " + template.getName(), body, command).send();
            return null;
        }
        if (existing != null) {
            manager.deleteTemplate(existing);
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. Templates must be enabled to be used. See:\n" +
                "- " + CM.grant_template.enable.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.delete.cmd.toSlashMention() + "\n" +
                "- " + CM.grant_template.send.cmd.toSlashMention();
    }


    // grant_template send <template> <receiver> <partial> <expire>
    @Command
    public String templateSend(@Me GuildDB db, @Me User user, @Me Member selfMember, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                               AGrantTemplate template,
                               DBNation receiver,
                               @Switch("e") @Timediff Long expire,
                               @Switch("d") @Timediff Long decay,
                               @Switch("i") Boolean ignore,
                               @Switch("p") String customValue,
                               @Switch("em") EscrowMode escrowMode,
                               @Switch("f") boolean force) throws IOException {
        boolean hasAdmin = Roles.ECON.has(selfMember);
        if (expire != null && expire == 0) expire = null;
        if (decay != null && decay == 0) decay = null;
        if (ignore == null) ignore = template.allowsIgnore();
        if (expire != null && !hasAdmin) {
            if (!template.allowsExpire()) {
                throw new IllegalArgumentException("The template `" + template.getName() + "` does not allow expiration");
            }
            if (expire < template.getExpire()) {
                throw new IllegalArgumentException("The template `" + template.getName() + "` does not allow expiration less than " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, template.getExpire()) + "ms");
            }
        }
        if (decay != null && !hasAdmin) {
            if (!template.allowsDecay()) {
                throw new IllegalArgumentException("The template `" + template.getName() + "` does not allow decay");
            }
            if (decay < template.getDecay()) {
                throw new IllegalArgumentException("The template `" + template.getName() + "` does not allow decay less than " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, template.getDecay()) + "ms");
            }
        }
        if (ignore && !template.allowsIgnore() && !hasAdmin) {
            throw new IllegalArgumentException("The template `" + template.getName() + "` does not allow `#ignore`");
        }

        Role econRole = template.getEconRole();
        if (econRole == null) {
            throw new IllegalArgumentException("The template `" + template.getName() + "` does not have an `econRole` set. Please set one via " + template.getType().getCommandMention());
        }
        Role selfRole = template.getSelfRole();

        boolean hasEconRole = hasAdmin || selfMember.getRoles().contains(econRole);
        if (!hasEconRole) {
            if (receiver.getNation_id() != me.getNation_id() || selfRole == null) {
                throw new IllegalArgumentException("You must have the role `" + econRole.getName() + "` to send grants to other nations");
            }
            // check has self role
            if (selfRole == null || !selfMember.getRoles().contains(selfRole)) {
                throw new IllegalArgumentException("You must have the role `" + selfRole.getName() + "` to send grants to yourself");
            }
        }

        Object parsed = template.parse(receiver, customValue);

        DepositType.DepositTypeInfo note = template.getDepositType(receiver, parsed);
        if (ignore) {
            note = note.ignore(true);
        }
        double[] cost = template.getCost(db, me, receiver, parsed);
        List<Grant.Requirement> requirements = template.getDefaultRequirements(db, me, receiver, parsed);
        String instructions = template.getInstructions(me, receiver, parsed);

        for (int i = 0; i < cost.length; i++) {
            if (cost[i] < 0)
                cost[i] = 0;
        }

        if (!force) {
            String title = "Send grant: " + template.getName();
            StringBuilder body = new StringBuilder();
            body.append(template.toFullString(me, receiver, parsed));
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }

        OffshoreInstance offshore = db.getOffshore();

        Map<Long, AccessType> accessType = new HashMap<>();
        for (Long id : Roles.MEMBER.getAllowedAccounts(selfMember.getUser(), db)) {
            accessType.put(id, AccessType.ECON);
        }
        TaxBracket taxAccount = template.getTaxAccount(db, receiver);
        accessType.put(db.getIdLong(), AccessType.ECON);
        if (template.getFromBracket() > 0) {
            if (db.isAllianceId(receiver.getAlliance_id())) {
                accessType.put((long) receiver.getAlliance_id(), AccessType.ECON);
            }
        }

        GrantTemplateManager manager = db.getGrantTemplateManager();

        Map<Long, Double> rankLimits = GuildKey.GRANT_TEMPLATE_LIMITS.getOrNull(db);
        Double limit = null;
        if (rankLimits != null && !rankLimits.isEmpty()) {
            Member myRoles = selfMember;
            for (Map.Entry<Long, Double> entry : rankLimits.entrySet()) {
                Role role = db.getGuild().getRoleById(entry.getKey());
                if (role == null) continue;
                if (myRoles.getRoles().contains(role)) {
                    if (limit == null) limit = 0d;
                    limit = Math.max(limit, entry.getValue());
                }
            }
            if (limit == null) {
                throw new IllegalArgumentException("Grant template limits are set (see: " + CM.settings.info.cmd.key(GuildKey.GRANT_TEMPLATE_LIMITS.name()).toSlashMention() + ")\n" +
                        "However you have none of the roles set in the limits.");
            }
        }

        TransferResult status;
        synchronized (OffshoreInstance.BANK_LOCK) {
            for (Grant.Requirement requirement : requirements) {
                if (requirement.canOverride()) continue;
                if (!requirement.apply(receiver.asNation())) {
                    return "Failed requirement (2): " + requirement.getMessage();
                }
            }
            if (limit != null) { // limits

                Long duration = GuildKey.GRANT_LIMIT_DELAY.getOrNull(db);
                if (duration == null) duration = TimeUnit.DAYS.toMillis(1);
                // if duration < 2h
                if (duration < TimeUnit.HOURS.toMillis(2)) {
                    throw new IllegalArgumentException("Grant template limits are set (see: " + CM.settings.info.cmd.toSlashMention() + " with key " + GuildKey.GRANT_LIMIT_DELAY.name() + ")\n" +
                            "However the duration is less than 2 hours.");
                }
                // get grants in past timeframe
                List<GrantTemplateManager.GrantSendRecord> records = manager.getRecordsBySender(me.getNation_id());
                long cutoff = System.currentTimeMillis() - duration;
                double totalOverDuration = 0;
                for (GrantTemplateManager.GrantSendRecord record : records) {
                    if (record.date < cutoff) continue;
                    totalOverDuration += ResourceType.convertedTotal(record.amount);
                }
                double total = totalOverDuration + ResourceType.convertedTotal(cost);
                if (total > limit) {
                    throw new IllegalArgumentException("You have a grant template limit of ~$" + MathMan.format(limit) +
                            " however have withdrawn ~$" + MathMan.format(totalOverDuration) + " over the past `" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, duration) + "` " +
                            " and are requesting ~$" + MathMan.format(ResourceType.convertedTotal(cost)) +
                            " which exceeds your limit by ~$" + MathMan.format(total - limit) + "\n" +
                            "`note: Figures are equivalent market value, not straight cash`\n" +
                            "See: " + GuildKey.GRANT_TEMPLATE_LIMITS.getCommandMention());
                }
            }

            status = offshore.transferFromNationAccountWithRoleChecks(
                    () -> accessType,
                    selfMember.getUser(),
                    null,
                    null,
                    taxAccount,
                    db,
                    null,
                    receiver,
                    cost,
                    note,
                    expire,
                    decay,
                    null,
                    false,
                    escrowMode,
                    false,
                    false);

            //in the case an unknown error occurs while sending the grant
            if (status.getStatus() == OffshoreInstance.TransferStatus.OTHER) {
                Set<Integer> blacklist = GuildKey.GRANT_TEMPLATE_BLACKLIST.getOrNull(db);
                if (blacklist == null) blacklist = new HashSet<>();
                blacklist.add(receiver.getId());
                GuildKey.GRANT_TEMPLATE_BLACKLIST.set(db, user, blacklist);

                Role role = Roles.ECON.toRole(db);
                String econGovMention = role == null ? "" : role.getAsMention();

                throw new IllegalArgumentException(status.getMessageJoined(true) + "\n" + econGovMention);
            }


            //saves grant record into the database
            if (status.getStatus().isSuccess()) {
                GrantTemplateManager.GrantSendRecord record = new GrantTemplateManager.GrantSendRecord(template.getName(), me.getId(), receiver.getId(), template.getType(), cost, System.currentTimeMillis());
                db.getGrantTemplateManager().saveGrantRecord(record);
            }
        }

        // setup String Builder
        StringBuilder grantMessage = new StringBuilder();

        //checks if receiver is null, if not then ping them
        if (receiver.getUser() != null)
            grantMessage.append(receiver.getUser().getAsMention());

        //build a string consisting of the template name, status, and instructions
        grantMessage.append("\n");
        grantMessage.append(status.toEmbedString());
        grantMessage.append("\n");
        grantMessage.append(instructions);

        String title = template.getName() + " grant sent to " + receiver.getName();
        io.create().embed(title, grantMessage.toString()).send();
        return null;
    }

    // register all the require commands
    // grant_template require __key__ AGrantTemplate [args]

    // template.addRequirement(requirement)
    // requirements are nation filters

    // grant_template unrequire <filter>


    // ----------------- old stuff below, ignore it ----------- //

    /*
    // 1111111111111111111111111111111111111111111111111111111111111222222222222222222222222222222222222222
            // #grant=533640709337514025 #expire=1680066316000 #land=2000 #city=12345678 #banker=1234567 #cash

        CITY

        LAND

        INFRA

        RESOURCES

        PROJECT

        BUILD

        WARCHEST
            - cities
            - units
            - mmr
            - rebuy



     */
//    public String city(@Default DBCity) {
//
//    }

    /*
    Econ staff can send grants

    Grantable tax rates
        unit grants use warchest as note
        warchest grants are to fill up since last warchest grant + war costs
            // if no wars declared and performed attacks in since last warchest, then those wars do not count

        // econ staff can override safe checks
        // members on 70/70 can grant themselves if they are >70% taxes and MEMBERS_CAN_GRANT is set to true
            // Does not apply to warchest grants

        Restrictions:
        Projects:
     */
//
//    public String send(GuildDB db, User author, DBNation me, Map<DBNation, Grant> grants, Map<DBNation, List<String>> errors, DepositType type, boolean onlyMissingFunds, Long expire, boolean countAsCash) {
//        // no funds need to be sent
//        boolean econGov = Roles.ECON.has(author, db.getGuild());
//        boolean econStaff = Roles.ECON_STAFF.has(author, db.getGuild());
//
//        if (!econGov) {
//            if (!econStaff) {
//                if (expire != null && expire < TimeUnit.DAYS.toMillis(120)) throw new IllegalArgumentException("Minimum expire date is 120d");
//            }
//            if (expire != null && expire < TimeUnit.DAYS.toMillis(120)) throw new IllegalArgumentException("Minimum expire date is 60d");
//        }
//
//        if (countAsCash) {
//            if (db.getOrNull(GuildKey.RESOURCE_CONVERSION) == Boolean.TRUE || econStaff) {
//                grants.entrySet().forEach(f -> f.getValue().addNote("#cash"));
//            } else {
//                throw new IllegalArgumentException("RESOURCE_CONVERSION is disabled. Only a staff member can use `#cash`");
//            }
//        }
//        // add basic requirements
//        grants.entrySet().forEach(new Consumer<Map.Entry<DBNation, Grant>>() {
//            @Override
//            public void accept(Map.Entry<DBNation, Grant> entry) {
//                Grant grant = entry.getValue();
//                DBNation nation = entry.getKey();
//                User user = nation.getUser();
//                if (user == null) {
//                    errors.computeIfAbsent(nation, f -> new ArrayList<>()).add("Nation is not verified: " + CM.register.cmd.toSlashMention() + "");
//                    entry.setValue(null);
//                    return;
//                }
//                Member member = db.getGuild().getMember(user);
//                if (member == null) {
//                    entry.setValue(null);
//                    errors.computeIfAbsent(nation, f -> new ArrayList<>()).add("Nation was not found in guild");
//                }
//
//                DBAlliance alliance = db.getAlliance();
//                grant.addRequirement(new Grant.Requirement("This guild is not part of an alliance", econGov, f -> alliance != null));
//                grant.addRequirement(new Grant.Requirement("Nation is not a member of an alliance", econGov, f -> f.getPosition() > 1));
//                grant.addRequirement(new Grant.Requirement("Nation is in VM", econGov, f -> f.getVm_turns() == 0));
//                grant.addRequirement(new Grant.Requirement("Nation is not in the alliance: " + alliance, econGov, f -> alliance != null && f.getAlliance_id() == alliance.getAlliance_id()));
//
//                Role temp = Roles.TEMP.toRole(db.getGuild());
//                grant.addRequirement(new Grant.Requirement("Nation not eligible for grants (has role: " + temp.getName() + ")", econStaff, f -> !member.getRoles().contains(temp)));
//
//                grant.addRequirement(new Grant.Requirement("Nation is not active in past 24h", econStaff, f -> f.active_m() < 1440));
//                grant.addRequirement(new Grant.Requirement("Nation is not active in past 7d", econGov, f -> f.active_m() < 10000));
//
//                grant.addRequirement(new Grant.Requirement("Nation does not have 5 raids going", econStaff, f -> f.getCities() >= 10 || f.getOff() >= 5));
//
//                if (nation.getNumWars() > 0) {
//                    // require max barracks
//                    grant.addRequirement(new Grant.Requirement("Nation does not have 5 barracks in each city (raiding)", econStaff, f -> f.getMMRBuildingStr().charAt(0) == '5'));
//                }
//
//                if (nation.getCities() >= 10 && nation.getNumWars() == 0) {
//                    // require 5 hangars
//                    grant.addRequirement(new Grant.Requirement("Nation does not have 5 hangars in each city (peacetime)", econStaff, f -> f.getMMRBuildingStr().charAt(2) == '5'));
//                    if (type == DepositType.CITY || type == DepositType.INFRA || type == DepositType.LAND) {
//                        grant.addRequirement(new Grant.Requirement("Nation does not have 0 factories in each city (peacetime)", econStaff, f -> f.getMMRBuildingStr().charAt(1) == '0'));
//                        grant.addRequirement(new Grant.Requirement("Nation does not have max aircraft", econStaff, f -> f.getMMR().charAt(2) == '5'));
//                    }
//                }
//
//                if (type != DepositType.WARCHEST) grant.addRequirement(new Grant.Requirement("Nation is beige", econStaff, f -> !f.isBeige()));
//                grant.addRequirement(new Grant.Requirement("Nation is gray", econStaff, f -> !f.isGray()));
//                grant.addRequirement(new Grant.Requirement("Nation is blockaded", econStaff, f -> !f.isBlockaded()));
//
//                // TODO no disburse past 5 days during wartime
//                // TODO 2d seniority and 5 won wars for initial 1.7k infra grants
//                grant.addRequirement(new Grant.Requirement("Nation does not have 10d seniority", econStaff, f -> f.allianceSeniority() >= 10));
//
//                grant.addRequirement(new Grant.Requirement("Nation does not have 80% daily logins (past 1 weeks)", econStaff, f -> nation.avg_daily_login_week() > 0.8));
//                if (nation.getCities() < 10 && type != DepositType.WARCHEST) {
//                    // mmr = 5000
//                    grant.addRequirement(new Grant.Requirement("Nation is not mmr=5000 (5 barracks, 0 factories, 0 hangars, 0 drydocks in each city)\n" +
//                            "(peacetime raiding below city 10)", econStaff, f -> f.getMMRBuildingStr().startsWith("5000")));
//                }
//
//                switch (type) {
//                    case WARCHEST:
//                        grant.addRequirement(new Grant.Requirement("Nation does not have 10 cities", econStaff, f -> f.getCities() >= 10));
//                        grant.addRequirement(new Grant.Requirement("Nation is losing", econStaff, f -> f.getRelativeStrength() < 1));
//                        grant.addRequirement(new Grant.Requirement("Nation is on low military", econStaff, f -> f.getAircraftPct() < 0.7));
//
//                        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(120);
//                        double[] allowed = getAllowedWarchest(db, nation, cutoff, econStaff);
//
//                        grant.addRequirement(new Grant.Requirement("Amount sent over past 120 days exceeds WARCHEST_PER_CITY\n" +
//                                "Tried to send: " + PW.resourcesToString(grant.cost()) + "\n" +
//                                "Allowance: " + PW.resourcesToString(allowed), econGov, f -> {
//                            double[] cost = grant.cost();
//                            for (int i = 0; i < allowed.length; i++) {
//                                if (cost[i] > allowed[i] + 0.01) return false;
//                            }
//                            return true;
//                        }));
//
//                        grant.addRequirement(new Grant.Requirement("Nation does not have 10 cities", econStaff, f -> f.getCities() >= 10));
//                        grant.addRequirement(new Grant.Requirement("Nation is losing", econStaff, f -> f.getRelativeStrength() < 1));
//                        grant.addRequirement(new Grant.Requirement("Nation is on low military", econStaff, f -> f.getAircraftPct() < 0.7));
//                        grant.addRequirement(new Grant.Requirement("Already received warchest since last war", econGov, new Function<DBNation, Boolean>() {
//                            @Override
//                            public Boolean apply(DBNation nation) {
//                                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3);
//                                long latestWarTime = 0;
//                                DBWar latestWar = null;
//                                for (DBWar war : nation.getWars()) {
//                                    if (war.date > latestWarTime) {
//                                        latestWarTime = war.date;
//                                        latestWar = war;
//                                    }
//                                }
//                                if (latestWar != null) {
//                                    for (AbstractCursor attack : latestWar.getAttacks()) {
//                                        latestWarTime = Math.max(latestWarTime, attack.epoch);
//                                    }
//                                    cutoff = Math.min(latestWarTime, cutoff);
//                                }
//                                List<Transaction2> transactions = nation.getTransactions(-1L);
//                                for (Transaction2 transaction : transactions) {
//                                    if (transaction.tx_datetime < cutoff) continue;
//                                    if (transaction.note != null && transaction.note.toLowerCase().contains("#warchest")) {
//                                        return false;
//                                    }
//                                }
//                                return true;
//                            }
//                        }));
//
//
//                        // has not received warchest in past 3 days
//                        // is assigned to a counter
//
//                        // fighting an enemy, or there are enemies
//
//                        boolean isCountering = false;
//                        Set<Integer> allies = db.getAllies(true);
//                        WarCategory warChannel = db.getWarChannel();
//                        for (Map.Entry<Integer, WarCategory.WarRoom> entry2 : warChannel.getWarRoomMap().entrySet()) {
//                            WarCategory.WarRoom room = entry2.getValue();
//                            if (room.isParticipant(nation, false)) {
//                                boolean isDefending = false;
//                                boolean isEnemyAttackingMember = false;
//                                for (DBWar war : room.target.getActiveWars()) {
//                                    if (allies.contains(war.defender_aa)) {
//                                        isEnemyAttackingMember = true;
//                                    }
//                                    if (war.defender_id == nation.getNation_id()) {
//                                        isDefending = true;
//                                    }
//                                }
//                                if (!isDefending && isEnemyAttackingMember) {
//                                    isCountering = true;
//                                }
//                            }
//                        }
//
//                        boolean isLosing = nation.getRelativeStrength() < 1;
//
//                        boolean hasEnemy = false;
//                        Set<Integer> enemies = db.getCoalition(Coalition.ENEMIES);
//
//                        boolean correctMMR = PW.matchesMMR(nation.getMMRBuildingStr(), "555X");
//
//                        // is assigned counter
//                        // OR
//                        // enemies AND mmr=555X
//
//                        // TODO
//                        // - funds for only a specific unit
//                        // - limit 24h
//                        // - dont allow more than 5 since last war
//                        // - dont allow more than 1 in 5 days if no units were bought in last X days
//
//                /*
//                Nation has max aircraft and 3% tanks
//                 */
//                        if (!enemies.isEmpty() && nation.getRelativeStrength() >= 1) {
//                            String mmr = nation.getMMRBuildingStr();
//                            // 80% planes + 50%
//                        }
//                        // has enemies, or ordered to counter
//
//                /*
//                    Has enemies and has not received warchest in the past 5 days
//                    Added to a war room as an attacker
//                    Has not received warchest in the past 5 days
//                 */
//
//                        break;
//                    case PROJECT:
//                        Project project = Projects.get(grant.getAmount());
//                        grant.addRequirement(new Grant.Requirement("Domestic policy must be set to TECHNOLOGICAL_ADVANCEMENT for project grants: <https://politicsandwar.com/nation/edit/>", econStaff, f -> f.getDomesticPolicy() == DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT));
//                        grant.addRequirement(new Grant.Requirement("Nation still has a project timer", econGov, f -> f.getProjectTurns() <= 0));
//                        grant.addRequirement(new Grant.Requirement("Nation has no free project slot", econGov, f -> f.projectSlots() > f.getNumProjects()));
//
//                        Set<Project> allowedProjects = db.getHandler().getAllowedProjectGrantSet(nation);
//
//                        grant.addRequirement(new Grant.Requirement("Recommended projects are: " + StringMan.getString(allowedProjects), econStaff, f -> allowedProjects.contains(project)));
//                        grant.addRequirement(new Grant.Requirement("Already have project", false, f -> {
//                            return !f.getProjects().contains(project);
//                        }));
//                        if (project == Projects.URBAN_PLANNING || project == Projects.ADVANCED_URBAN_PLANNING || project == Projects.METROPOLITAN_PLANNING) {
//                            grant.addRequirement(new Grant.Requirement("Please contact econ gov to approve this grant (as its expensive)", econStaff, f -> {
//                                return !f.getProjects().contains(project);
//                            }));
//                        }
//                        for (Project required : project.requiredProjects()) {
//                            grant.addRequirement(new Grant.Requirement("Missing required project: " + required.name(), false, f -> f.hasProject(required)));
//                        }
//                        if (project.requiredCities() > 1) {
//                            grant.addRequirement(new Grant.Requirement("Project requires " + project.requiredCities() + " cities", false, f -> f.getCities() >= project.requiredCities()));
//                        }
//                        if (project.maxCities() > 0) {
//                            grant.addRequirement(new Grant.Requirement("Project cannot be built above " + project.requiredCities() + " cities", false, f -> f.getCities() <= project.maxCities()));
//                        }
//
//                        grant.addRequirement(new Grant.Requirement("Already received a grant for a project in past 10 days", econGov, new Function<DBNation, Boolean>() {
//                            @Override
//                            public Boolean apply(DBNation nation) {
//                                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
//
//                                List<Transaction2> transactions = nation.getTransactions(-1L);
//
//                                if (Grant.hasReceivedGrantWithNote(transactions, "#project", cutoff)) return false;
//
//                                if (Grant.getProjectsGranted(nation, transactions).contains(project)) return false;
//                                return true;
//                            }
//                        }));
//
//                        break;
//                    case CITY: {
//                        int upTo = Integer.parseInt(grant.getAmount());
//                        if (upTo > 1) {
//                            grant.addRequirement(new Grant.Requirement("City timer prevents purchasing more cities after c10", econGov, f -> {
//                                int max = Math.max(10, f.getCities() + 1);
//                                return upTo <= max;
//                            }));
//                        }
//
//
//                        grant.addRequirement(new Grant.Requirement("Nation already has 20 cities", true, f -> f.getCities() < 20));
//                        grant.addRequirement(new Grant.Requirement("Nation does not have 10 cities", true, f -> f.getCities() >= 10));
//
//                        grant.addRequirement(new Grant.Requirement("Domestic policy must be set to MANIFEST_DESTINY for city grants: <https://politicsandwar.com/nation/edit/>", econStaff, f -> f.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY));
//
//                        grant.addRequirement(new Grant.Requirement("It is recommended to alternate city and project timers after c15", true, f -> f.getProjectTurns() <= 0));
//
//                        grant.addRequirement(new Grant.Requirement("Nation still has a city timer", econGov, f -> f.getCities() < 10 || f.getCityTurns() <= 0));
//
//                        int currentCities = nation.getCities();
//                        grant.addRequirement(new Grant.Requirement("Nation has built a city, please run the grant command again", false, f -> f.getCities() == currentCities));
//
//                        grant.addRequirement(new Grant.Requirement("Nation received city grant in past 10 days", econGov, new Function<DBNation, Boolean>() {
//                            @Override
//                            public Boolean apply(DBNation nation) {
//                                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
//                                List<Transaction2> transactions = nation.getTransactions(-1L);
//                                return (!Grant.hasReceivedGrantWithNote(transactions, "#city", cutoff));
//                            }
//                        }));
//
//                        grant.addRequirement(new Grant.Requirement("Already received a grant for a city", econStaff, new Function<DBNation, Boolean>() {
//                            @Override
//                            public Boolean apply(DBNation nation) {
//                                List<Transaction2> transactions = nation.getTransactions(-1);
//                                return !Grant.hasGrantedCity(nation, transactions, currentCities + 1);
//                            }
//                        }));
//                        break;
//                    }
//                    case INFRA: {
//                        // TODO check has not received an infra grant for that city
//
//                        double amount = Double.parseDouble(grant.getAmount());
//                        Map<Integer, JavaCity> cities = nation.getCityMap(true, false);
//
//                        Set<Integer> grantCities = grant.getCities();
//                        Set<Integer> isForPoweredCities = new HashSet<>();
//                        for (Integer grantCity : grantCities) {
//                            JavaCity city = cities.get(grantCity);
//                            if (city.getPowered(nation::hasProject) && city.getRequiredInfra() > city.getInfra() && city.getRequiredInfra() > 1450) {
//                                isForPoweredCities.add(grantCity);
//                            }
//                        }
//
//                        grant.addRequirement(new Grant.Requirement("Grant is for powered city with damaged infra (ids: " + StringMan.getString(isForPoweredCities), true, f -> {
//                            return isForPoweredCities.isEmpty();
//                        }));
//                        Map<Integer, Double> infraGrants = Grant.getInfraGrantsByCity(nation, nation.getTransactions());
//                        grant.addRequirement(new Grant.Requirement("City already received infra grant (ids: " + StringMan.getString(infraGrants), econStaff, f -> {
//                            // if city current infra is less than infra grant
//                            return infraGrants.isEmpty();
//                        }));
//
//
//                        Grant.Requirement noWarRequirement = new Grant.Requirement("Higher infra grants require approval when fighting wars", econStaff, new Function<DBNation, Boolean>() {
//                            @Override
//                            public Boolean apply(DBNation nation) {
//                                if (nation.getDef() > 0) return false;
//                                if (nation.getNumWars() > 0 && nation.getNumWarsAgainstActives() > 0) return false;
//                                return true;
//                            }
//                        });
//
//                        Grant.Requirement seniority = new Grant.Requirement("Nation does not have 3 days alliance seniority", econStaff, f -> f.allianceSeniority() < 3);
//
//                        // if amount is uneven
//                        if (amount % 50 != 0) {
//                            grant.addRequirement(new Grant.Requirement("Amount must be a multiple of 50", false, f -> false));
//                        }
//
//                        if (amount >= 1500) {
//                            grant.addRequirement(seniority);
//                            grant.addRequirement(new Grant.Requirement("Infra grants are restricted during wartime. Please contact econ (or remove the `enemies` coalition)", econStaff, f -> db.getCoalition2(Coalition.ENEMIES).isEmpty()));
//                            grant.addRequirement(noWarRequirement);
//                        }
//                        if (amount > 1700) {
//                            grant.addRequirement(new Grant.Requirement("Nation does not have 10 cities", econStaff, f -> f.getCities() >= 10));
//                        }
//                        if (amount >= 2000) {
//                            grant.addRequirement(seniority);
//                            grant.addRequirement(noWarRequirement);
//                            grant.addRequirement(new Grant.Requirement("Infra grants are restricted during wartime. Please contact econ", econStaff, f -> db.getCoalition2(Coalition.ENEMIES).isEmpty()));
//                            grant.addRequirement(new Grant.Requirement("Domestic policy must be set to URBANIZATION for infra grants above 1700: <https://politicsandwar.com/nation/edit/>", econStaff, f -> f.getDomesticPolicy() == DomesticPolicy.URBANIZATION));
//                            grant.addRequirement(new Grant.Requirement("Infra grants above 1700 whilst raiding/warring require econ approval", econStaff, f -> {
//                                if (f.getDef() > 0) return false;
//                                if (f.getOff() > 0) {
//                                    for (DBWar war : f.getActiveWars()) {
//                                        DBNation other = war.getNation(war.isAttacker(f));
//                                        if (other.active_m() < 1440 || other.getPosition() >= Rank.APPLICANT.id) {
//                                            return false;
//                                        }
//                                    }
//                                }
//                                return true;
//                            }));
//                        }
//                        int max = 2000;
//                        if (nation.getCities() > 15) max = 2250;
//                        if (nation.getCities() > 20) max = 2500;
//                        if (amount > 2000) {
//                            // todo
//                            // 10-15 2k
//                            // 15-20 2.25k
//                            // 20-25 2.5k
//                            grant.addRequirement(new Grant.Requirement("Nation does not have 10 cities", econStaff, f -> f.getCities() >= 10));
//                        }
//
//                        break;
//                    }
//                    case LAND:
//
//
//                    case BUILD:
//                        // MMR of build matches required MMR
//                    case UNIT:
//                    case RESOURCES: {
//                        grant.addRequirement(new Grant.Requirement("Amount must be a multiple of 50", econGov, f -> false));
//                        GuildMessageChannel channel = db.getOrNull(GuildKey.RESOURCE_REQUEST_CHANNEL);
//                        if (channel != null) {
//                            throw new IllegalArgumentException("Please use " + CM.transfer.self.cmd.toSlashMention() + " or `" + Settings.commandPrefix(true) + "disburse` in " + channel.getAsMention() + " to request funds from your deposits");
//                        }
//                    }
//                    default:
//
//                        throw new UnsupportedOperationException("TODO: This type of grant is not supported currently");
//                        break;
//                }
//
//            }
//        });
//
//        // 60 day minimum expire for staff
//
//        // if no econ perms, only 1 nation, and has to be self
//        //
//        return "TODO";
//    }
//
//    public double[] getAllowedWarchest(@Me GuildDB db, DBNation nation, long cutoff, boolean addWarchestBase) {
//        double[] warchestMax = PW.resourcesToArray(db.getPerCityWarchest(nation));
//        List<DBWar> wars = nation.getWars();
//
//        Set<Long> banks = db.getTrackedBanks();
//        List<Map.Entry<Integer, Transaction2>> transactions = nation.getTransactions(db, banks, true, true, 0, 0);
//
//        double[] transfers = ResourceType.getBuffer();
//
//        for (Map.Entry<Integer, Transaction2> entry : transactions) {
//            Transaction2 tx = entry.getValue();
//            if (tx.note == null || tx.tx_datetime < cutoff) continue;
//            Map<String, String> notes = PW.parseTransferHashNotes(tx.note);
//            if (!notes.containsKey("#warchest")) continue;
//            int sign = entry.getKey();
//            if (sign > 0 && tx.tx_id > 0 && !notes.containsKey("#ignore")) continue;
//
//            transfers = PW.add(transfers, PW.multiply(tx.resources, sign));
//        }
//
//        double[] warExpenses = ResourceType.getBuffer();
//
//        long offensiveLeeway = TimeUnit.DAYS.toMillis(14);
//        for (int i = 0; i < wars.size(); i++) {
//            DBWar war = wars.get(i);
//            if (war.date < cutoff) continue;
//            if (war.attacker_id == nation.getNation_id()) {
//                if (!banks.contains(war.attacker_aa)) continue;
//                if (war.status == WarStatus.EXPIRED || war.status == WarStatus.DEFENDER_VICTORY) {
//                    List<AbstractCursor> attacks = war.getAttacks();
//                    attacks.removeIf(f -> f.attacker_nation_id != nation.getNation_id());
//                    if (attacks.isEmpty()) continue;
//                }
//                // was against an inactive, or a non enemy
//            } else {
//                if (!banks.contains(war.defender_aa)) continue;
//                if (war.status == WarStatus.ATTACKER_VICTORY || war.status == WarStatus.EXPIRED || war.status == WarStatus.PEACE) {
//                    List<AbstractCursor> attacks = war.getAttacks();
//                    attacks.removeIf(f -> f.attacker_nation_id != nation.getNation_id());
//                    if (attacks.isEmpty()) continue;
//
//                    boolean hasOffensive = false;
//                    for (int j = i - 1; j >= 0; j--) {
//                        DBWar other = wars.get(j);
//                        if (war.date - other.date > offensiveLeeway) break;
//                        if (other.attacker_id == nation.getNation_id()) {
//                            hasOffensive = true;
//                            break;
//                        }
//                    }
//                    if (!hasOffensive) {
//                        for (int j = i + 1; j < wars.size(); j++) {
//                            DBWar other = wars.get(j);
//                            if (other.date - war.date > offensiveLeeway) break;
//                            if (other.attacker_id == nation.getNation_id()) {
//                                hasOffensive = true;
//                                break;
//                            }
//                        }
//                        if (!hasOffensive) continue;
//                    }
//                }
//
//                double[] cost = PW.resourcesToArray(war.toCost().getTotal(war.isAttacker(nation), true, false, true, true));
//                for (int j = 0; j < cost.length; j++) {
//                    double amt = cost[j];
//                    if (amt > 0) warExpenses[j] += amt;
//                }
//                // add war cost
//
//            }
//        }
//
//        double[] total = ResourceType.getBuffer();
//        if (addWarchestBase) total = ResourceType.add(total, warchestMax);
//        total = ResourceType.add(total, transfers);
//        total = ResourceType.add(total, warExpenses);
//        for (int i = 0; i < total.length; i++) total[i] = Math.max(0, Math.min(warchestMax[i], total[i]));
//
//        return total;
//    }
//
//
//
//    // for all cities
//    // for new cities
//    @Command(desc = "Calculate and send funds for specified military units")
//    @RolePermission(Roles.MEMBER)
//    public String unit(@Me GuildDB db, @Me User author, @Me DBNation me, Set<DBNation> nations, MilitaryUnit unit, @Switch("q") Integer quantity,
//                       @Switch("p") @Range(min =0, max=100) Double percent,
//                       @Switch("a") double sendAttackConsumption,
//                       @Switch("n") Integer isForNewCities,
//
//                       @Switch("m") MMRDouble capAtMMR,
//                       @Switch("b") boolean capAtCurrentMMR,
//                       @Switch("c") boolean multiplyPerCity,
//
//                       @Switch("u") boolean onlySendMissingUnits,
//
//                       @Switch("f") boolean forceOverrideChecks,
//
//                       @Switch("o") boolean onlySendMissingFunds,
//                       @Switch("e") @Timediff Long expire,
//                       @Switch("cash") boolean countAsCash) {
//
//        if (unit.getBuilding() == null && (capAtMMR != null || multiplyPerCity || percent != null || isForNewCities != null)) {
//            throw new IllegalArgumentException("Unit " + unit + " is not valid with the arguments: capAtMMR,multiplyPerCity,percent,isForNewCities");
//        }
//        if ((quantity == null) == (percent == null)) throw new IllegalArgumentException("Please specify `percent` OR `quantity`, not both");
//        if (percent != null && multiplyPerCity) throw new IllegalArgumentException("multiplyPerCity is only valid for absolute values");
//
//        // Map<DBNation, double[]> fundsToSend, Map<DBNation, List<String>> notes, Map<DBNation, String> instructions, Map<DBNation, List<String>> errors
//        Map<DBNation, Grant> grants = new HashMap<>();
//        Map<DBNation, List<String>> errors = new HashMap<>();
//
//        if (nations.size() > 200) throw new IllegalArgumentException("Too many nations (Max 200)");
//        if (nations.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet()).size() > 4) throw new IllegalArgumentException("Too many alliances (max 4)");
//
//        for (DBNation nation : nations) {
//            int currentAmt = nation.getUnits(unit);
//            int numCities = isForNewCities != null ? isForNewCities : nation.getCities();
//
//            Double natQuantity = (double) quantity;
//            if (percent != null) {
//                natQuantity = numCities * unit.getBuilding().max() * unit.getBuilding().cap(nation::hasProject) * percent / 100d;
//            }
//            if (multiplyPerCity) {
//                natQuantity *= numCities;
//            }
//
//            Map<Integer, JavaCity> cities = nation.getCityMap(false);
//            Set<JavaCity> previousCities = cities.values().stream().filter(f -> f.getAge() >= 1).collect(Collectors.toSet());
//
//            int currentUnits = nation.getUnits(unit);
//            if (isForNewCities != null) {
//                if (previousCities.isEmpty()) {
//                    currentUnits = 0;
//                } else {
//                    currentUnits = Math.max(0, unit.getCap(() -> previousCities, nation::hasProject) - currentUnits);
//                }
//            }
//
//            int cap = Integer.MAX_VALUE;
//            if (capAtMMR != null) {
//                cap = (int) (capAtMMR.get(unit) * unit.getBuilding().max() * numCities);
//            }
//            if (capAtCurrentMMR) {
//                if (isForNewCities == null) {
//                    cap = Math.min(cap, unit.getCap(nation, false));
//                } else {
//                    MMRDouble mmr = new MMRDouble(nation.getMMRBuildingArr());
//                    cap = Math.min(cap, (int) (mmr.get(unit) * unit.getBuilding().max() * numCities));
//                }
//            }
//            natQuantity = Math.min(natQuantity, cap);
//
//            int unitsToSend = (int) (onlySendMissingUnits ? Math.max(natQuantity - nation.getUnits(unit), 0) : natQuantity);
//
//            double[] funds = unit.getCost(unitsToSend);
//            if (sendAttackConsumption > 0) {
//                funds = ResourceType.add(funds, PW.multiply(unit.getConsumption().clone(), sendAttackConsumption));
//            }
//
//            if (ResourceType.isEmpty(funds)) {
//                errors.computeIfAbsent(nation, f -> new ArrayList<>()).add("No funds need to be sent");
//                continue;
//            }
//
//            double[] finalFunds = funds;
//            Grant grant = new Grant(nation, DepositType.WARCHEST)
//                    .setInstructions(DepositType.UNIT.instructions + "\n" + unit + "=" + MathMan.format(natQuantity))
//                    .setTitle(unit.name())
//                    .setCost(f -> finalFunds)
//                    .addRequirement(new Grant.Requirement("Nation units have changed, try again", false, f -> f.getUnits(unit) == currentAmt));
//            ;
//
//            grants.put(nation, grant);
//        }
//
//        return send(db, author, me, grants, errors, DepositType.UNIT, onlySendMissingFunds, expire, countAsCash);
//    }
//
//    @Command(desc = "Send funds for mmr")
//    public String mmr(@Me GuildDB db, @Me User author, @Me DBNation me, Set<DBNation> nations,
//                      @Arg("MMR worth of units to send (only missing)") MMRDouble grantMMR,
//                      @Switch("r") @Arg("Additional rebuy worth of units `10/10/10/6` would be two full rebuys") MMRDouble rebuyMMR,
//                      @Switch("b") boolean switchBuildFunds,
//                      @Switch("a") @Arg("Funds for unit consumption 10/10/10/10 would be funds for 10 attacks of each uni (at max MMR)") MMRDouble sendConsumptionForAttacks,
//                      @Switch("p") boolean ignoreLowPopulation,
//                      @Switch("o") boolean onlySendMissingFunds,
//                      @Switch("e") @Timediff Long expire) {
//        Map<DBNation, double[]> amountsToSendMap = new HashMap<>();
//        for (DBNation nation : nations) {
//            double[] funds = ResourceType.getBuffer();
//
//            for (MilitaryUnit unit : MilitaryUnit.values) {
//                if (unit.getBuilding() == null) continue;
//                int cap;
//                if (ignoreLowPopulation) {
//                    cap = unit.getBuilding().cap(nation::hasProject) * unit.getBuilding().max() * nation.getCities();
//                } else {
//                    cap = unit.getCap(nation, false);
//                }
//                int amt = Math.max(0, (int) (cap * grantMMR.getPercent(unit)) - nation.getUnits(unit));
//                if (rebuyMMR != null) amt += rebuyMMR.getPercent(unit) * cap;
//                if (amt > 0) {
//                    funds = ResourceType.add(funds, unit.getCost(amt));
//                }
//            }
//
//            if (sendConsumptionForAttacks != null) {
//                for (MilitaryUnit unit : MilitaryUnit.values) {
//                    double numAttacks = sendConsumptionForAttacks.get(unit);
//                    if (numAttacks > 0) {
//                        funds = ResourceType.add(funds, PW.multiply(unit.getConsumption().clone(), numAttacks));
//                    }
//                }
//            }
//            if (switchBuildFunds) {
//                for (DBCity entry : nation._getCitiesV3().entrySet()) {
//                    DBCity city = entry.getValue();
//                    for (MilitaryUnit unit : MilitaryUnit.values) {
//                        double required = grantMMR.get(unit);
//                        if (required <= 0) continue;
//                        int current = city.get(unit.getBuilding());
//                        if (current < required) {
//                            int toBuy = (int) Math.ceil(required - current);
//                            unit.getBuilding().cost(funds, toBuy);
//                        }
//                    }
//                }
//            }
//            amountsToSendMap.put(nation, funds);
//        }
//        return send(db, author, me, amountsToSendMap, onlySendMissingFunds, expire, note, instructions);
//    }
//
//    @Command(desc = "Send funds for a city")
//    @RolePermission(Roles.MEMBER)
//    public String city(@Me GuildDB db, @Me User author, @Me DBNation me, Set<DBNation> nations,
//                       int buyUpToCity,
//                       @Switch("r") boolean citiesAreAdditional,
//                       @Switch("d") boolean forceManifestDestiny,
//                       @Switch("u") boolean forceUrbanPlanning,
//                       @Switch("a") boolean forceAdvancedUrbanPlanning,
//                       @Switch("m") boolean forceMetropolitanPlanning,
//
//                       @Switch("p") boolean includeProjectPurchases,
//                       @Switch("b") @Arg("Requires the force project argument as true") boolean onlyForceProjectIfCheaper,
//                       @Switch("i") Double includeInfraGrant,
//                       @Switch("l") Double includeLandGrant,
//                       @Switch("j") CityBuild includeCityBuildJsonCost,
//                       @Switch("mmr") MMRInt includeNewUnitCost,
//                       @Switch("w") boolean includeNewCityWarchest,
//                       @Switch("o") boolean onlySendMissingFunds,
//                       @Switch("e") @Timediff Long expire
//    ) {
//        if (onlyForceProjectIfCheaper && !forceAdvancedUrbanPlanning && !forceUrbanPlanning && !forceMetropolitanPlanning) {
//            throw new IllegalArgumentException("`onlyBuyProjectIfCheaper` is enabled, but no project purchases are: forceAdvancedUrbanPlanning,forceUrbanPlanning,forceMetropolitanPlanning");
//        }
//
//        Map<DBNation, List<String>> notesMap = new HashMap<>();
//        Map<DBNation, double[]> amountsToSendMap = new HashMap<>();
//
//        for (DBNation nation : nations) {
//            int cityTo = citiesAreAdditional ? nation.getCities() + buyUpToCity : buyUpToCity;
//            if (cityTo <= nation.getCities()) continue;
//            int numCities = cityTo - nation.getCities();
//
//            boolean manifest = nation.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY || forceManifestDestiny;
//            boolean up = nation.hasProject(Projects.URBAN_PLANNING) || (forceUrbanPlanning && !onlyForceProjectIfCheaper);
//            boolean aup = nation.hasProject(Projects.ADVANCED_URBAN_PLANNING) || (forceAdvancedUrbanPlanning && !onlyForceProjectIfCheaper);
//            boolean mp = nation.hasProject(Projects.METROPOLITAN_PLANNING) || (forceMetropolitanPlanning && !onlyForceProjectIfCheaper);
//
//            List<String> notes = new ArrayList<>();
//            double[] funds = ResourceType.getBuffer();
//
//            if (!up && forceUrbanPlanning && cityTo > Projects.URBAN_PLANNING.requiredCities()) {
//                double cost1 = PW.cityCost(nation.getCities(), cityTo, manifest, up, aup, mp);
//                double[] projectCost = Projects.URBAN_PLANNING.cost(true);
//                double cost2 = PW.cityCost(nation.getCities(), cityTo, manifest, true, aup, mp) + PW.convertedTotal(projectCost);
//                if (cost2 <= cost1) {
//                    up = true;
//                    if (includeProjectPurchases) funds = PW.add(funds, projectCost);
//                }
//            }
//            if (!aup && forceAdvancedUrbanPlanning && cityTo > Projects.ADVANCED_URBAN_PLANNING.requiredCities()) {
//                double cost1 = PW.cityCost(nation.getCities(), cityTo, manifest, up, aup, mp);
//                double[] projectCost = Projects.ADVANCED_URBAN_PLANNING.cost(true);
//                double cost2 = PW.cityCost(nation.getCities(), cityTo, manifest, up, true, mp) + PW.convertedTotal(projectCost);
//                if (cost2 <= cost1) {
//                    aup = true;
//                    if (includeProjectPurchases) funds = PW.add(funds, projectCost);
//                }
//            }
//            if (!mp && forceUrbanPlanning && cityTo > Projects.METROPOLITAN_PLANNING.requiredCities()) {
//                double cost1 = PW.cityCost(nation.getCities(), cityTo, manifest, up, aup, mp);
//                double[] projectCost = Projects.METROPOLITAN_PLANNING.cost(true);
//                double cost2 = PW.cityCost(nation.getCities(), cityTo, manifest, up, aup, true) + PW.convertedTotal(projectCost);
//                if (cost2 <= cost1) {
//                    mp = true;
//                    if (includeProjectPurchases) funds = PW.add(funds, projectCost);
//                }
//            }
//
//            funds[0] += PW.cityCost(nation.getCities(), cityTo, manifest, up, aup, mp);
//
//            if (includeCityBuildJsonCost != null) {
//                if (includeCityBuildJsonCost.getLand() != null) {
//                    includeLandGrant = includeCityBuildJsonCost.getLand();
//                }
//                if (includeCityBuildJsonCost.getInfraNeeded() != null) {
//                    includeInfraGrant = includeCityBuildJsonCost.getInfraNeeded().doubleValue();
//                }
//                JavaCity city = new JavaCity(includeCityBuildJsonCost);
//                for (Building building : Buildings.values()) {
//                    int amt = city.get(building);
//                    if (amt > 0) {
//                        building.cost(funds, amt * numCities);
//                    }
//                }
//            }
//            if (includeInfraGrant != null && includeInfraGrant > 10) {
//                funds[0] += PW.calculateInfra(10, includeInfraGrant) * numCities;
//            }
//            if (includeLandGrant != null && includeLandGrant > 250) {
//                funds[0] += PW.calculateLand(250, includeLandGrant) * numCities;
//            }
//
//            if (includeNewUnitCost != null) {
//                for (MilitaryUnit unit : MilitaryUnit.values()) {
//                    double numBuildings = includeNewUnitCost.get(unit);
//                    if (numBuildings > 0) {
//                        int units = (int) (unit.getBuilding().max() * numBuildings * numCities);
//                        funds = ResourceType.add(funds, unit.getCost(units));
//                    }
//                }
//            }
//            if (includeNewCityWarchest) {
//                funds = ResourceType.add(funds, PW.multiply(PW.resourcesToArray(db.getPerCityWarchest(nation)), numCities));
//            }
//            amountsToSendMap.put(nation, funds);
//        }
//
//        Grant.getInfraGrantsByCity()
//        return send(db, author, me, amountsToSendMap, onlySendMissingFunds, expire, note, instructions);
//    }
//
//    @Command(desc = "Send funds for a city")
//    @RolePermission(Roles.MEMBER)
//    public String infra(@Me GuildDB db, @Me User author, @Me DBNation me, Set<DBNation> nations,
//                       int buyUpToInfra,
//                       @Switch("d") DBCity forCity,
//                       @Switch("p") boolean buyFromPreviousGrantLevel,
//                       @Switch("b") boolean forNewCity,
//                       @Switch("a") boolean forceAdvancedEngineeringCorps,
//                       @Switch("c") boolean forceCenterForCivilEngineering,
//                       @Switch("u") boolean forceUrbanization,
//                       @Switch("g") boolean forceGovernmentSupportAgency,
//                       @Switch("o") boolean onlySendMissingFunds,
//                       @Switch("e") @Timestamp Long expire,
//                       @Switch("n") String note
//    ) {
//        // anyone can run the grant command
//        // displays current infra level -> amount
//
//        Map<DBNation, Grant> grants = new HashMap<>();
//        Map<DBNation, List<String>> errors = new HashMap<>();
//
//        for (DBNation nation : nations) {
//            double[] funds = ResourceType.getBuffer();
//
//            Map<Integer, Double> currentInfraLevels = new HashMap<>();
//
//            Grant grant = new Grant(nation, DepositType.INFRA);
//
//            if (forNewCity) {
//                // requires econ gov
//                if (!Roles.ECON.has(author, db.getGuild())) {
//                    grant.addRequirement(new Grant.Requirement("Requires ECON role to send funds for a new city. Use the `forCity` argument instead", false, f -> Roles.ECON.has(author, db.getGuild())));
//                }
//                currentInfraLevels.put(-1, 10d);
//            } else if (forCity != null) {
//                if (!nation._getCitiesV3().containsKey(forCity.getId())) {
//                    errors.computeIfAbsent(nation, f -> new ArrayList<>()).add("No city with id " + forCity.getId() + " found in nation " + nation.getName());
//                    continue;
//                }
//                currentInfraLevels.put(forCity.id, forCity.infra);
//                grant.setCities(Collections.singleton(forCity.id));
//            } else {
//                grant.setAllCities();
//                // set current infra levels from nation's cities
//            }
//            // process buyFromPreviousGrantLevel
//            if (buyFromPreviousGrantLevel) {
//                List<Transaction2> transactions = null;
//                for (Map.Entry<Integer, Double> entry : currentInfraLevels.entrySet()) {
//                    if (entry.getKey() != -1) {
//                        if (transactions == null) transactions = nation.getTransactions();
//                        double previous = Grant.getCityInfraGranted(nation, entry.getKey(), transactions);
//                        entry.setValue(Math.max(previous, entry.getValue()));
//                    }
//                }
//            }
//
//            // remove any that have that much infra akready
//
//            // if  newinfralevels is empty
//            if (currentInfraLevels.isEmpty()) {
//                errors.computeIfAbsent(nation, f -> new ArrayList<>()).add("No infra needs to be granted for " + nation.getNation() + " (already has sufficient)");
//                continue;
//            }
//
//            boolean aec = nation.hasProject(Projects.ADVANCED_ENGINEERING_CORPS) || forceAdvancedEngineeringCorps;
//            boolean cce = nation.hasProject(Projects.CENTER_FOR_CIVIL_ENGINEERING) || forceCenterForCivilEngineering;
//            boolean gsa = nation.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY) || forceGovernmentSupportAgency;
//            boolean urb = nation.getDomesticPolicy() == DomesticPolicy.URBANIZATION || forceUrbanization;
//
//            grant.setCost(f -> {
//                double total = 0;
//                for (Map.Entry<Integer, Double> entry : currentInfraLevels.entrySet()) {
//                    if (entry.getValue() < buyUpToInfra) {
//                        total += PW.calculateInfra(entry.getValue(), buyUpToInfra, aec, cce, gsa, urb);
//                    }
//                }
//                return ResourceType.MONEY.toArray(total);
//            });
//
//            // set instructions
//            // use @ symbol to buy
//
//            grant.setAmount(buyUpToInfra);
//
//        }
//        return send(db, author, me, amountsToSendMap, onlySendMissingFunds, expire, note);
//    }
//
//
//
//     infra
//     build
//     land
//     project
//     resources
//     warchest
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String resources() {
//
//    }
//
//
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String project() {
//
//    }
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String infra() {
//
//    }
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String land(NationList nations, double landUpTo, @Default CityFilter cities, @Switch("m") boolean onlyMissingFunds, @Switch("e") int expireAfterDays, @Switch("f") boolean bypassChecks) {
//
//    }

    @Command
    @HasOffshore
    @RolePermission(value = {Roles.ECON_STAFF, Roles.ECON, Roles.ECON_WITHDRAW_SELF}, any = true)
    public String withdrawEscrowed(@Me OffshoreInstance offshore, @Me IMessageIO channel, @Me JSONObject command, @Me GuildDB db, @Me DBNation me, @Me User author, DBNation receiver, Map<ResourceType, Double> amount,
                                   @Switch("f") boolean force) throws IOException {
        if (GuildKey.MEMBER_CAN_ESCROW.getOrNull(db) != Boolean.TRUE && !Roles.ECON_STAFF.has(author, db.getGuild())) {
            return "To enable member withdrawal of escrowed funds, see: " + CM.settings.info.cmd.key(GuildKey.MEMBER_CAN_ESCROW.name());
        }
        // Require ECON_STAFF if receiver is not me
        if (receiver.getId() != me.getId()) {
            if (!Roles.ECON_STAFF.has(author, db.getGuild())) {
                return "You cannot withdraw escrowed resources for other nations. Missing role: " + Roles.ECON_STAFF.toDiscordRoleNameElseInstructions(db.getGuild());
            }
        }
        // Ensure none of amount is negative
        double[] amtArr = ResourceType.resourcesToArray(amount);
        for (ResourceType type : ResourceType.values) {
            if (amtArr[type.ordinal()] < 0) {
                return "Cannot withdraw negative amount of " + type.getName() + "=" + MathMan.format(amtArr[type.ordinal()]);
            }
        }


        if (!TimeUtil.checkTurnChange()) {
            return "Cannot withdraw escrowed resources close to turn change";
        }

        if (receiver.getVm_turns() > 0) {
            throw new IllegalArgumentException("Receiver " + receiver.getUrl() + "is in Vacation Mode");
        }
        if (receiver.isBlockaded() && !force) {
            channel.create().confirmation("Error: Nation is blockaded!", "Do you want to try sending anyway?", command).send();
            return null;
        }

        if (!force) {
            String title = "Send escrowed to " + receiver.getNation();
            StringBuilder body = new StringBuilder();
            if (db.isAlliance() && !receiver.isAlliance()) {
                body.append("**Warning: **`Not in alliance`\n");
            }
            if (receiver.active_m() > 2880) {
                body.append("**Warning: **`Inactive for ").append(TimeUtil.secToTime(TimeUnit.MINUTES, receiver.active_m())).append("`\n");
            }
            body.append(receiver.getNationUrlMarkup(true) + " | " + receiver.getAllianceUrlMarkup(true)).append("\n");
            channel.create().confirmation(title, body.toString(), command).send();
            return null;
        }

        // get a lock for nation
        final Object lock = OffshoreInstance.NATION_LOCKS.computeIfAbsent(receiver.getId(), f -> new Object());
        synchronized (lock) {
            if (OffshoreInstance.FROZEN_ESCROW.containsKey(receiver.getId())) {
                return "Cannot withdraw escrowed resources for " + receiver.getNation() + " because it is frozen. Please have an admin use " + CM.offshore.unlockTransfers.cmd.toSlashMention();
            }
            Map.Entry<double[], Long> escrowedPair = db.getEscrowed(receiver);
            if (escrowedPair == null || ResourceType.isZero(escrowedPair.getKey())) {
                return "No escrowed resources found for " + receiver.getNation();
            }
            long escrowDate = escrowedPair.getValue();
            // Ensure transfer amount is <= escrowed
            for (ResourceType type : ResourceType.values) {
                if (Math.round(amtArr[type.ordinal()] * 100) > Math.round(escrowedPair.getKey()[type.ordinal()] * 100)) {
                    return "Cannot withdraw more than escrowed for " + type.getName() + "=" + MathMan.format(amtArr[type.ordinal()]) + " > " + MathMan.format(escrowedPair.getKey()[type.ordinal()]) + "\n" +
                            "See: " + CM.deposits.check.cmd.toSlashMention();
                }
            }
            //  - Deduct from escrowed
            double[] newEscrowed = new double[ResourceType.values.length];
            boolean hasEscrowed = false;
            for (ResourceType type : ResourceType.values) {
                long newAmtCents = Math.round((escrowedPair.getKey()[type.ordinal()] - amtArr[type.ordinal()]) * 100);
                hasEscrowed |= newAmtCents > 0;
                newEscrowed[type.ordinal()] = newAmtCents * 0.01;
            }
            StringBuilder message = new StringBuilder();
            message.append("Deducted `"  + ResourceType.resourcesToString(amtArr) + "` from escrow account for " + receiver.getNation() + "\n");
            if (!hasEscrowed) {
                db.setEscrowed(receiver, null, escrowDate);
            } else {
                db.setEscrowed(receiver, newEscrowed, escrowDate);
            }
            { // - Ensure amt is deducted
                Map.Entry<double[], Long> checkEscrowedPair = db.getEscrowed(receiver);
                double[] checkEscrowed = checkEscrowedPair == null ? ResourceType.getBuffer() : checkEscrowedPair.getKey();
                for (ResourceType type : ResourceType.values) {
                    if (Math.round(checkEscrowed[type.ordinal()] * 100) != Math.round(newEscrowed[type.ordinal()] * 100)) {
                        OffshoreInstance.FROZEN_ESCROW.put(receiver.getId(), true);
                        message.append("Failed to deduct escrowed resources for " + type.getName() + "=" + MathMan.format(checkEscrowed[type.ordinal()]) + " != " + MathMan.format(newEscrowed[type.ordinal()]));
                        message.append("\n");
                        // add amount deducted
                        message.append("Funds were deducted but the in-game transfer was aborted\n");
                        message.append("Econ gov may need to correct your escrow balance via " + CM.escrow.add.cmd.toSlashMention() + "\n");
                        message.append("Original escrowed: `" + ResourceType.resourcesToString(escrowedPair.getKey()) + "`\n");
                        message.append("Expected escrowed: `" + ResourceType.resourcesToString(newEscrowed) + "`\n");
                        message.append("Current escrowed: `" + ResourceType.resourcesToString(checkEscrowed) + "`\n");
                        message.append("The `expected` and `new` should match, but something went wrong when deducting the balance.\n");
                        // econ role mention
                        Role role = Roles.ECON.toRole(db);
                        if (role != null) {
//                            message.append(role.getAsMention());
                        }
                        return message.toString();
                    }
                }
            }
            // - Send to self via #ignore
            TransferResult result = offshore.transferFromAllianceDeposits(me, db, db::isAllianceId, receiver, amtArr, "#ignore");
            switch (result.getStatus()) {
                case SENT_TO_ALLIANCE_BANK:
                case SUCCESS: {
                    channel.create().embed("Escrow " + result.toTitleString(), result.toEmbedString()).send();
                    return null;
                }
                default:
                case OTHER: {
                    channel.create().embed("Escrow " + result.toTitleString(), result.toEmbedString()).send();
                    return null;
                }
                case TURN_CHANGE:
                case BLOCKADE:
                case MULTI:
                case INSUFFICIENT_FUNDS:
                case INVALID_DESTINATION:
                case VACATION_MODE:
                case NOTHING_WITHDRAWN:
                case INVALID_API_KEY:
                case ALLIANCE_ACCESS:
                case APPLICANT:
                case INACTIVE:
                case BEIGE:
                case GRAY:
                case NOT_MEMBER:
                case INVALID_NOTE:
                case INVALID_TOKEN:
                case GRANT_REQUIREMENT:
                case AUTHORIZATION:
                case CONFIRMATION: {
                    // add balance back
                    db.setEscrowed(receiver, escrowedPair.getKey(), escrowDate);
                    result.addMessage("Adding back `" + ResourceType.resourcesToString(amtArr) + "` to escrow account for " + receiver.getMarkdownUrl());
                    channel.create().embed("Escrow " + result.toTitleString(), result.toEmbedString()).send();
                    return null;
                }
            }
        }
    }

    @WhitelistPermission
    @Command
    @RolePermission(Roles.MEMBER)
    public synchronized String grants(@Me GuildDB db, DBNation nation) {
        String baseUrl = WebRoot.REDIRECT + "/" + db.getIdLong() + "/";
        List<String> pages = Arrays.asList(
                baseUrl + "infragrants/" + nation.getNation_id(),
                baseUrl + "landgrants/" + nation.getNation_id(),
                baseUrl + "citygrants/" + nation.getNation_id(),
                baseUrl + "projectgrants/" + nation.getNation_id()
        );
        StringBuilder response = new StringBuilder();
        response.append(nation.toMarkdown() + "\n");
        response.append("color=" + nation.getColor() + "\n");
        response.append("mmr[unit]=" + nation.getMMR() + "\n");
        response.append("mmr[build]=" + nation.getMMRBuildingStr() + "\n");
        response.append("timer[city]=" + nation.getCityTurns() + " timer[project]=" + nation.getProjectTurns() + "\n");
        response.append("slots[project]=" + nation.getNumProjects() + "/" + nation.projectSlots() + "\n");
        response.append("activity[turn]=" + MathMan.format(nation.avg_daily_login_turns() * 100) + "%\n");
        response.append("activity[day]=" + MathMan.format(nation.avg_daily_login() * 100) + "%\n");
        return response + "<" + StringMan.join(pages, ">\n<") + ">";
    }
    //
    private Set<Integer> disabledNations = new HashSet<>();
//
//    @WhitelistPermission
//    @Command
//    @RolePermission(Roles.ECON_STAFF)
//    public synchronized String approveGrant(@Me DBNation banker, @Me User user, @Me IMessageIO io, @Me JSONObject command, @Me GuildDB db, UUID key, @Switch("f") boolean force) {
//        OffshoreInstance offshore = db.getOffshore();
//        if (offshore == null) {
//            return "No offshore bank";
//        }
//        try {
//            Grant grant = Grant.getApprovedGrant(db.getIdLong(), key);
//            if (grant == null) {
//                return "Invalid Token. Please try again";
//            }
//            DBNation receiver = grant.getNation();
//
//            receiver.updateTransactions();
//            receiver.getCityMap(true);
//
//            Set<Grant.Requirement> requirements = grant.getRequirements();
//            Set<Grant.Requirement> failed = new HashSet<>();
//            Set<Grant.Requirement> override = new HashSet<>();
//            for (Grant.Requirement requirement : requirements) {
//                Boolean result = requirement.apply(receiver);
//                if (!result) {
//                    if (requirement.canOverride()) {
//                        override.add(requirement);
//                    } else {
//                        failed.add(requirement);
//                    }
//                }
//            }
//
//            if (!failed.isEmpty()) {
//                StringBuilder result = new StringBuilder("Grant could not be approved.\n");
//                if (!failed.isEmpty()) {
//                    result.append("\nFailed checks:\n - " + StringMan.join(failed.stream().map(f -> f.getMessage()).collect(Collectors.toList()), "\n - ") + "\n");
//                }
//                if (!override.isEmpty()) {
//                    result.append("\nFailed checks that you have permission to bypass:\n - " + StringMan.join(override.stream().map(f -> f.getMessage()).collect(Collectors.toList()), "\n - ") + "\n");
//                }
//                return result.toString();
//            }
//
//            if (!force) {
//                String title = grant.title();
//                StringBuilder body = new StringBuilder();
//
//                body.append("Receiver: " + receiver.getNationUrlMarkup(true) + " | " + receiver.getAllianceUrlMarkup(true)).append("\n");
//                body.append("Note: " + grant.getNote()).append("\n");
//                body.append("Amt: " + grant.getAmount()).append("\n");
//                body.append("Cost: `" + PW.resourcesToString(grant.cost())).append("\n\n");
//
//                if (!override.isEmpty()) {
//                    body.append("**" + override.size() + " failed checks (you have admin override)**\n - ");
//                    body.append(StringMan.join(override.stream().map(f -> f.getMessage()).collect(Collectors.toList()), "\n - ") + "\n\n");
//                }
//
//                io.create().confirmation(title, body.toString(), command).send();
//                return null;
//            }
//            if (disabledNations.contains(receiver.getNation_id())) {
//                return "There was an error processing the grant. Please contact an administrator";
//            }
//
//            Grant.deleteApprovedGrant(db.getIdLong(), key);
//
//            disabledNations.add(receiver.getNation_id());
//
//            Map.Entry<OffshoreInstance.TransferStatus, String> result = offshore.transferFromAllianceDeposits(banker, db, f -> allowedAlliances.contains((long) f), receiver, grant.cost(), grant.getNote());
//            OffshoreInstance.TransferStatus status = result.getKey();
//
//            StringBuilder response = new StringBuilder();
//            if (status == OffshoreInstance.TransferStatus.SUCCESS || status == OffshoreInstance.TransferStatus.ALLIANCE_BANK) {
//                response.append("**Transaction:** ").append(result.getValue()).append("\n");
//                response.append("**Instructions:** ").append(grant.getInstructions());
//
//                Locutus.imp().getExecutor().submit(new Runnable() {
//                    @Override
//                    public void run() {
//                        receiver.updateTransactions();
//                        for (Grant.Requirement requirement : grant.getRequirements()) {
//                            if (!requirement.apply(receiver)) {
//                                disabledNations.remove(receiver.getNation_id());
//                                return;
//                            }
//                        }
//                        AlertUtil.error("Grant is still eligable",grant.getType() + " | " + grant.getNote() + " | " + grant.getAmount() + " | " + grant.getTitle());
//                    }
//                });
//
//            } else {
//                if (status != OffshoreInstance.TransferStatus.OTHER) {
//                    disabledNations.remove(receiver.getNation_id());
//                }
//                response.append(status + ": " + result.getValue());
//            }
//
//
//            return response.toString();
//        } catch (Throwable e) {
//            e.printStackTrace();
//            throw e;
//        }
//    }
}