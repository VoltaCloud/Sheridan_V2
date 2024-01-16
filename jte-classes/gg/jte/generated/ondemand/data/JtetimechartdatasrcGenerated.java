package gg.jte.generated.ondemand.data;
import com.google.gson.JsonObject;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
public final class JtetimechartdatasrcGenerated {
	public static final String JTE_NAME = "data/timechartdatasrc.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,3,3,3,7,7,7,7,7,7,7,7,7,7,3,4,5,6,6,6,6};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, String title, JsonObject data, boolean time) {
		jteOutput.writeContent("<div class=\"locutus-chart\" data-src=\"");
		jteOutput.writeUserContent(data.toString());
		jteOutput.writeContent("\" title=\"");
		jteOutput.writeUserContent(title);
		jteOutput.writeContent("\" time=\"");
		jteOutput.writeUserContent(time);
		jteOutput.writeContent("\"></div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		String title = (String)params.get("title");
		JsonObject data = (JsonObject)params.get("data");
		boolean time = (boolean)params.get("time");
		render(jteOutput, jteHtmlInterceptor, ws, title, data, time);
	}
}
