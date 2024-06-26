//package link.locutus.discord.commands.fun;
//
//import link.locutus.discord.Locutus;
//import link.locutus.discord.config.Settings;
//import link.locutus.discord.util.RateLimitUtil;
//import link.locutus.discord.util.discord.DiscordUtil;
//import net.dv8tion.jda.api.OnlineStatus;
//import net.dv8tion.jda.api.entities.Guild;
//import net.dv8tion.jda.api.entities.Member;
//import net.dv8tion.jda.api.entities.Mentions;
//import net.dv8tion.jda.api.entities.User;
//import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
//
//import java.util.List;
//
//public class Tag {
//    private Long it;
//    private Long previous;
//
//    public String tag(MessageReceivedEvent event) {
//        if (guild == null) {
//            return null;
//        }
//
//        if (it != null) {
//            User user = Locutus.imp().getDiscordApi().getUserById(it);
//            List<Guild> mutual = user.getMutualGuilds();
//            if (mutual.isEmpty()) {
//                it = null;
//                previous = null;
//            } else {
//                Member member = mutual.get(0).getMemberById(it);
//                if (member == null || (member.getOnlineStatus() != OnlineStatus.ONLINE && member.getOnlineStatus() != OnlineStatus.DO_NOT_DISTURB)) {
//                    it = null;
//                    previous = null;
//                } else {
//                    return user.getName() + " is it!";
//                }
//            }
//        }
//        Member member = guild.getMember(author);
//        if (it == null) {
//            assert member != null;
//            if ((member.getOnlineStatus() != OnlineStatus.ONLINE && member.getOnlineStatus() != OnlineStatus.DO_NOT_DISTURB)) {
//                return "You can only play tag if you are online.";
//            }
//            previous = it;
//            it = author.getIdLong();
//            return "Tag, you're it!";
//        }
//        return null;
//    }
//
//    public Long getIt() {
//        return it;
//    }
//
////    public void checkTag(Guild guild, User author, String fullCommandRaw) {
////        if (guild != null && it != null && it.equals(author.getIdLong()) && !author.isBot()) {
////            Mentions mentions = event.getMessage().getMentions();
////            for (Member mention : mentions.getMembers()) {
////                if ((mention.getOnlineStatus() == OnlineStatus.ONLINE || mention.getOnlineStatus() == OnlineStatus.DO_NOT_DISTURB)) {
////                    String msg = "%s tagged %s. %s is now it. Run for your lives!";
////                    msg = String.format(msg, author.getName(), mention.getEffectiveName(), mention.getEffectiveName());
////                    if (it == Settings.INSTANCE.APPLICATION_ID) {
////                        it = null;
////                        previous = null;
////                    } else {
////                        Long tmp = previous;
////                        if (DiscordUtil.trimContent(fullCommandRaw).toLowerCase().contains("no backsies")) {
////                            previous = it;
////                        } else {
////                            previous = null;
////                        }
////                        if (tmp != null && mention.getIdLong() == tmp) {
////                            msg = "You cannot tag " + mention.getEffectiveName() + ", as the no backsies rule is in play.";
////                        } else {
////                            it = mention.getIdLong();
////                        }
////                    }
////                    RateLimitUtil.queue(channel.sendMessage(msg));
////                    return;
////                }
////            }
////        }
////    }
//}
