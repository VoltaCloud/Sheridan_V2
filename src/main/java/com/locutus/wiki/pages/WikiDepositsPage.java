package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiDepositsPage extends WikiGen {
    public WikiDepositsPage(CommandManager2 manager) {
        super(manager, "deposits");
    }

    @Override
    public String generateMarkdown() {
        return build(

        );
       /*
       Notes
       Expire

       Grant command

       /deposits shift
       /deposits convertNegative

       Itemized deposits
       /settings_bank_info DISPLAY_ITEMIZED_DEPOSITS

       Listing transfers
       /bank records
       /tax deposits

       Taxes into deposits -> Link to tax automation page

       Tracking other alliances
       Tracking offshores

       Addbalance

       Bulk add balance

       Withdraw
       NationAccount argument
       Transfer commands

       Checking deposits / deposits sheet
       Importing deposits sheet

       Resetting deposits

       Settings
           public static GuildSetting<Boolean> RESOURCE_CONVERSION = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {

           public static GuildSetting<MessageChannel> ADDBALANCE_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
           public static GuildSetting<MessageChannel> WITHDRAW_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
           public static GuildSetting<MessageChannel> BANK_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
           public static GuildSetting<Boolean> DISPLAY_ITEMIZED_DEPOSITS = new GuildBooleanSetting(GuildSettingCategory.BANK_INFO) {
           public static GuildSetting<MessageChannel> DEPOSIT_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {


       Member auto withdrawing
           public static GuildSetting<Boolean> MEMBER_CAN_OFFSHORE = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
    public static GuildSetting<Boolean> MEMBER_CAN_WITHDRAW = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
    public static GuildSetting<Boolean> MEMBER_CAN_WITHDRAW_WARTIME = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
    public static GuildSetting<Boolean> MEMBER_CAN_WITHDRAW_IGNORES_GRANTS = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
    public static GuildSetting<Long> BANKER_WITHDRAW_LIMIT = new GuildLongSetting(GuildSettingCategory.BANK_ACCESS) {
    public static GuildSetting<Long> BANKER_WITHDRAW_LIMIT_INTERVAL = new GuildLongSetting(GuildSettingCategory.BANK_ACCESS) {
        */
    }
}