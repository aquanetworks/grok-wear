package si.zya.grokwear;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class HomeButtonTest {
    private static JSONObject bot(String id,String name)throws Exception{
        return new JSONObject().put("id",id).put("name",name);
    }
    private static JSONArray roster()throws Exception{
        return new JSONArray().put(bot("primary","Main Bot")).put(bot("pin-a","Research Bot")).put(bot("pin-b","Travel Bot")).put(bot("other","Inbox Bot"));
    }
    private static JSONArray pins(){return new JSONArray().put("pin-a").put("pin-b");}

    @Test public void chosenBotWinsOverPrimary()throws Exception {
        JSONArray roster=roster();
        JSONObject primary=HomeButton.find(roster,"primary");
        HomeButton.Resolved r=HomeButton.resolve(new HomeButton.Choice("other","Inbox Bot"),roster,primary);
        assertFalse(r.stale);assertFalse(r.followsPrimary);assertFalse(r.picker());
        assertEquals("other",r.bot.optString("id"));
        assertEquals("Inbox Bot",r.bot.optString("name"));
    }
    @Test public void missingChoiceFallsBackToPrimaryAndIsStale()throws Exception {
        JSONArray roster=roster();
        JSONObject primary=HomeButton.find(roster,"primary");
        HomeButton.Resolved r=HomeButton.resolve(new HomeButton.Choice("gone","Old Bot"),roster,primary);
        assertTrue(r.stale);assertTrue(r.followsPrimary);assertFalse(r.picker());
        assertEquals("primary",r.bot.optString("id"));
    }
    @Test public void noChoiceUsesPrimary()throws Exception {
        JSONArray roster=roster();
        JSONObject primary=HomeButton.find(roster,"primary");
        HomeButton.Resolved r=HomeButton.resolve(new HomeButton.Choice("",""),roster,primary);
        assertFalse(r.stale);assertTrue(r.followsPrimary);
        assertEquals("primary",r.bot.optString("id"));
    }
    @Test public void noChoiceAndNoPrimaryOpensPicker()throws Exception {
        HomeButton.Resolved r=HomeButton.resolve(new HomeButton.Choice("",""),roster(),null);
        assertTrue(r.picker());assertNull(r.bot);assertFalse(r.stale);assertTrue(r.followsPrimary);
    }
    @Test public void staleWithoutPrimaryOpensPicker()throws Exception {
        HomeButton.Resolved r=HomeButton.resolve(new HomeButton.Choice("gone","Old"),roster(),null);
        assertTrue(r.stale);assertTrue(r.picker());assertNull(r.bot);
    }
    @Test public void pickerIsPinnedThenRest()throws Exception {
        List<JSONObject> order=HomeButton.picker(roster(),pins());
        assertEquals(4,order.size());
        assertEquals("pin-a",order.get(0).optString("id"));
        assertEquals("pin-b",order.get(1).optString("id"));
        assertEquals("primary",order.get(2).optString("id"));
        assertEquals("other",order.get(3).optString("id"));
    }
    @Test public void labelShowsStoredNameUntilRosterLoads() {
        assertEquals(HomeButton.PRIMARY,HomeButton.label(new HomeButton.Choice("",""),new JSONArray()));
        assertEquals("Travel Bot",HomeButton.label(new HomeButton.Choice("pin-b","Travel Bot"),new JSONArray()));
    }
    @Test public void labelPrefersLiveRosterName()throws Exception {
        assertEquals("Inbox Bot",HomeButton.label(new HomeButton.Choice("other","stale-name"),roster()));
    }
    @Test public void isChosenMatchesIdOnly() {
        HomeButton.Choice choice=new HomeButton.Choice("pin-a","Research Bot");
        assertTrue(HomeButton.isChosen(choice,"pin-a"));
        assertFalse(HomeButton.isChosen(choice,"other"));
        assertFalse(HomeButton.isChosen(new HomeButton.Choice("",""),"primary"));
    }
}
