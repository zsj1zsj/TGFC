package net.jejer.hipda.utils;

import android.content.Context;
import android.text.TextUtils;

import net.jejer.hipda.async.SimpleListLoader;
import net.jejer.hipda.bean.SimpleListBean;
import net.jejer.hipda.bean.SimpleListItemBean;
import net.jejer.hipda.bean.UserInfoBean;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

public class HiParser {

    public static SimpleListBean parseSimpleList(Context ctx, int type, Document doc) {

        // Async check notify
        new HiParserThreadList.parseNotifyRunnable(ctx, doc).run();

        switch (type) {
            case SimpleListLoader.TYPE_MYREPLY:
                return parseReplyList(ctx, doc);
            case SimpleListLoader.TYPE_MYPOST:
                return parseMyPost(ctx, doc);
            case SimpleListLoader.TYPE_SMS:
                return parseSMS(doc);
            case SimpleListLoader.TYPE_THREAD_NOTIFY:
                return parseNotify(doc);
            case SimpleListLoader.TYPE_SMS_DETAIL:
                return parseSmsDetail(doc);
            case SimpleListLoader.TYPE_SEARCH:
                return parseSearch(doc);
            case SimpleListLoader.TYPE_SEARCH_USER_THREADS:
                return parseSearch(doc);
            case SimpleListLoader.TYPE_FAVORITES:
                return parseFavorites(doc);
            case SimpleListLoader.TYPE_ATTENTION:
                return parseFavorites(doc);
        }

        return null;
    }

    private static SimpleListBean parseReplyList(Context ctx, Document doc) {
        if (doc == null) {
            return null;
        }

        Elements tableES = doc.select("table.datatable");
        if (tableES.size() == 0) {
            return null;
        }

        SimpleListBean list = new SimpleListBean();

        int last_page = 1;
        //if this is the last page, page number is in <strong>
        Elements pagesES = doc.select("div.pages_btns div.pages a");
        pagesES.addAll(doc.select("div.pages_btns div.pages strong"));
        if (pagesES.size() > 0) {
            for (Node n : pagesES) {
                int tmp = HttpUtils.getIntFromString(((Element) n).text());
                if (tmp > last_page) {
                    last_page = tmp;
                }
            }
        }
        list.setMaxPage(last_page);

        Elements trES = tableES.first().select("tr");

        SimpleListItemBean item = null;
        //first tr is title, skip
        for (int i = 1; i < trES.size(); ++i) {
            Element trE = trES.get(i);

            // odd have title, even have reply text;
            if (i % 2 == 1) {
                item = new SimpleListItemBean();

                // thread
                Elements thES = trE.select("th");
                if (thES.size() == 0) {
                    continue;
                }
                Elements linkES = thES.first().select("a");
                if (linkES.size() != 1) {
                    continue;
                }
                String tid = linkES.first().attr("href");
                if (!tid.startsWith("redirect.php?goto=")) {
                    continue;
                }
                item.setTid(HttpUtils.getMiddleString(tid, "ptid=", "&"));
                item.setPid(HttpUtils.getMiddleString(tid, "pid=", "&"));
                String title = linkES.first().text();

                // time
                Elements lastpostES = trE.select("td.lastpost");
                if (lastpostES.size() == 0) {
                    continue;
                }
                String time = lastpostES.first().text();

                item.setTitle(title);
                item.setTime(time);

                Elements forumES = trE.select("td.forum");
                if (forumES.size() > 0) {
                    item.setForum(forumES.first().text());
                }

            } else {
                list.add(item);

                Elements thES = trE.select("th");
                if (thES.size() == 0) {
                    continue;
                }
                item.setInfo(thES.first().text());
            }
        }
        return list;
    }

    private static SimpleListBean parseMyPost(Context ctx, Document doc) {
        if (doc == null) {
            return null;
        }

        Elements tableES = doc.select("table.datatable");
        if (tableES.size() == 0) {
            return null;
        }

        SimpleListBean list = new SimpleListBean();

        int last_page = 1;
        //if this is the last page, page number is in <strong>
        Elements pagesES = doc.select("div.pages_btns div.pages a");
        pagesES.addAll(doc.select("div.pages_btns div.pages strong"));
        if (pagesES.size() > 0) {
            for (Node n : pagesES) {
                int tmp = HttpUtils.getIntFromString(((Element) n).text());
                if (tmp > last_page) {
                    last_page = tmp;
                }
            }
        }
        list.setMaxPage(last_page);

        Elements trES = tableES.first().select("tr");

        SimpleListItemBean item = null;
        //first tr is title, skip
        for (int i = 1; i < trES.size(); ++i) {
            Element trE = trES.get(i);

            // odd have title, even have reply text;
            item = new SimpleListItemBean();

            // thread
            Elements thES = trE.select("th");
            if (thES.size() == 0) {
                continue;
            }
            Elements linkES = thES.first().select("a");
            if (linkES.size() != 1) {
                continue;
            }
            String tid = linkES.first().attr("href");
            if (!tid.startsWith("viewthread.php?tid=")) {
                continue;
            }
            tid = HttpUtils.getMiddleString(tid, "viewthread.php?tid=", "&");
            String title = linkES.first().text();

            // time
            Elements lastpostES = trE.select("td.lastpost");
            if (lastpostES.size() == 0) {
                continue;
            }
            String time = lastpostES.first().text();

            item.setTid(tid);
            item.setTitle(title);
            item.setTime(time);

            Elements forumES = trE.select("td.forum");
            if (forumES.size() > 0) {
                item.setForum(forumES.first().text());
            }

            list.add(item);
        }
        return list;
    }

    public static SimpleListBean parseSMS(Document doc) {
        if (doc == null) {
            return null;
        }

        Elements pmlistES = doc.select("table#pmlist tbody");
        if (pmlistES.size() < 1) {
            return null;
        }

        SimpleListBean list = new SimpleListBean();
        Elements liES = pmlistES.first().select("tr");

        for (int i = 0; i < liES.size(); ++i) {
            if (!liES.get(i).select("td").get(1).select("a").text().contains("您发表的帖子")) {
                Element liE = liES.get(i);
                SimpleListItemBean item = new SimpleListItemBean();


                // author and author uid
                Element pciteES = liE.select("td").get(2);
                Elements citeES = pciteES.select("a");
                if (citeES.size() == 0) {
                    continue;
                }
                item.setAuthor(citeES.first().text());
                item.setForum(item.getAuthor());
                Elements uidAES = citeES.first().select("a");
                if (uidAES.size() == 0) {
                    continue;
                }
                String uidHref = uidAES.first().attr("href");
                item.setUid(HttpUtils.getMiddleString(uidHref, "uid-", "."));

                // avatar
                item.setAvatarUrl(HiUtils.getAvatarUrlByUid(item.getUid()));
                // time
                item.setTime(liE.select("td").get(3).ownText());

                // new
                Element newES = liE.select("td").get(1);
                if (newES.attr("style") == "font-weight:800") {
                    item.setNew(true);
                }

                // info
                Element summaryES = liE.select("td a").get(0);

                item.setTitle(summaryES.text());

                // detail url
                String detailHref = liE.select("td a").get(0).attr("href");
                item.setDetailUrl(HiUtils.BaseUrl+detailHref);

                // pmid
                String pmid = HttpUtils.getMiddleString(detailHref,"pmid=","&");
                item.setPmid(pmid);

                list.add(item);
            }
        }

        return list;
    }

    public static SimpleListBean parseNotify(Document doc) {
        if (doc == null) {
            return null;
        }

        Elements feedES = doc.select("table#pmlist tbody");
        if (feedES.size() == 0) {
            return null;
        }

        SimpleListBean list = new SimpleListBean();
        Elements liES = feedES.first().select("tr");
        for (int i = 0; i < liES.size() && i < 15; ++i) {
            Element liE = liES.get(i);
            Elements divES = liE.select("td");
            if (divES.size() == 0) {
                continue;
            }
            SimpleListItemBean item = null;
            if (divES.first().hasClass("f_thread")) {
                // user reply your thread
                item = parseNotifyThread(divES.first());
            } else if (divES.get(1).select("a").toString().contains("您发表的帖子")) {
                // user quote your post
                item = parseNotifyQuoteandReply(divES);
            } else if (divES.first().hasClass("f_reply")) {
                // user reply your post
                item = parseNotifyQuoteandReply(divES);
            }

            if (item != null) {
                list.add(item);
            }
        }

        return list;
    }

    public static SimpleListItemBean parseNotifyThread(Element root) {
        SimpleListItemBean item = new SimpleListItemBean();
        String info = "";

        Elements aES = root.select("a");
        for (Element a : aES) {
            String href = a.attr("href");
            if (href.contains("space.php")) {
                // get replied usernames
                info += a.text() + " ";
            } else if (href.startsWith(HiUtils.BaseUrl + "redirect.php?from=notice&goto=findpost")) {
                // Thread Name and TID and PID
                item.setTitle(a.text());
                item.setTid(HttpUtils.getMiddleString(a.attr("href"), "ptid=", "&"));
                item.setPid(HttpUtils.getMiddleString(a.attr("href"), "pid=", "&"));
                break;
            }
        }

        // time
        Elements emES = root.select("em");
        if (emES.size() == 0) {
            return null;
        }
        item.setTime(emES.first().text());

        if (root.text().contains("回复了您关注的主题"))
            info += "回复了您关注的主题";
        else
            info += "回复了您的帖子 ";

        // new
        Elements imgES = root.select("img");
        if (imgES.size() > 0) {
            if (imgES.first().attr("src").equals("images/default/notice_newpm.gif")) {
                item.setNew(true);
            }
        }

        item.setInfo(info);
        return item;
    }

    public static SimpleListItemBean parseNotifyQuoteandReply(Elements td) {
        SimpleListItemBean item = new SimpleListItemBean();

        String detailHref = td.get(1).select("a").attr("href");
        String authorHref = td.get(2).select("a").attr("href");
        String uid = HttpUtils.getMiddleString(authorHref,"uid-",".");
        item.setUid(uid);
        item.setAuthor(td.get(2).text());
        item.setAvatarUrl(HiUtils.getAvatarUrlByUid(uid));
        item.setTitle(td.get(1).text());
        item.setTime(td.get(3).text());
        item.setDetailUrl(HiUtils.BaseUrl+detailHref);
        item.setInfo("");
        if (td.get(1).select("a").attr("style") == "font-weight:800"){
            item.setNew(true);
        }
        return item;
    }

    public static SimpleListItemBean parseNotifyQuoteandDetail(String rsp) {
        Document doc = Jsoup.parse(rsp);
        if (doc == null) {
            return null;
        }

        SimpleListItemBean item = new SimpleListItemBean();

        String tid = "";
        String pid = "";
        Elements quoteES = doc.select("div.content div.postmessage");
        if (quoteES.select("a").get(0).attr("href").contains("redirect.php")) {
            tid = HttpUtils.getMiddleString(quoteES.select("a").get(2).attr("href"), "tid=", "&");
            pid = HttpUtils.getMiddleString(quoteES.select("a").get(2).attr("href"), "pid=", "&");
        } else if (quoteES.select("a").size() == 2) {
            tid = HttpUtils.getMiddleString(quoteES.select("a").get(1).attr("href"), "tid=", "&");
            pid = HttpUtils.getMiddleString(quoteES.select("a").get(1).attr("href"), "pid=", "#");
        } else {
            tid = HttpUtils.getMiddleString(quoteES.select("a").get(0).attr("href"), "tid=", "&");
        }
        String author = HttpUtils.getMiddleString(quoteES.toString(), "以下您所发表的帖子被 ", "引用并通知您。");
        String yourInfo = HttpUtils.getMiddleString(quoteES.toString(),"</div>", "[/quote]");
        String quoteInfo = HttpUtils.getMiddleString(quoteES.toString(), "引用摘要:</strong>", "<br>");
        String info = "<u>您的帖子:</u>"+yourInfo + "<br><u>" + author + " 说:</u>" + quoteInfo;
        item.setInfo(info);
        item.setTid(tid);
        item.setPid(pid);

        return item;
    }

    private static SimpleListBean parseSmsDetail(Document doc) {
        if (doc == null) {
            return null;
        }

        Elements smsDetailES = doc.select("table tbody tr td.postcontent");

        Elements postInfoES = smsDetailES.select("p.postinfo").select("a");
        //get my uid and username
        String smsTime = HttpUtils.getMiddleString(smsDetailES.select("p.postinfo").text(), "时间:", ",");
        String myUsername = postInfoES.get(1).text();
        String myUid = HttpUtils.getMiddleString(postInfoES.get(1).attr("href"),"uid-",".");
        String author = postInfoES.get(0).text();
        String authorUid = HttpUtils.getMiddleString(postInfoES.get(0).attr("href"),"uid-",".");

        Elements postmessageES = smsDetailES.select("div.postmessage");
        String smsTitle = postmessageES.select("a").text();
        String mySmsDetail = postmessageES.select("div.quote blockquote").text();
        String smsDetail = postmessageES.text();

        SimpleListBean list = new SimpleListBean();
        SimpleListItemBean item = new SimpleListItemBean();

        // author
        item.setAuthor(author);
        item.setUid(authorUid);

        // avatar
        item.setAvatarUrl(HiUtils.getAvatarUrlByUid(item.getUid()));


        // time
        item.setTime(smsTime);

        // info
        item.setInfo(smsDetail);

        // new
        list.add(item);


        return list;
    }

    private static SimpleListBean parseSearch(Document doc) {
        if (doc == null) {
            return null;
        }

        SimpleListBean list = new SimpleListBean();
        int last_page = 1;

        //if this is the last page, page number is in <strong>
        Elements pagesES = doc.select("div.pages_btns div.pages a");
        pagesES.addAll(doc.select("div.pages_btns div.pages strong"));
        String searchIdUrl;
        if (pagesES.size() > 0) {
            searchIdUrl = pagesES.first().attr("href");
            if (searchIdUrl.contains("srchtype=fulltext")) {
                return parseSearchFullText(doc);
            }
            list.setSearchIdUrl(searchIdUrl);
            for (Node n : pagesES) {
                int tmp = HttpUtils.getIntFromString(((Element) n).text());
                if (tmp > last_page) {
                    last_page = tmp;
                }
            }
        }
        list.setMaxPage(last_page);

        Elements tbodyES = doc.select("tbody");
        for (int i = 0; i < tbodyES.size(); ++i) {
            Element tbodyE = tbodyES.get(i);
            SimpleListItemBean item = new SimpleListItemBean();

            Elements subjectES = tbodyE.select("tr th.subject");
            if (subjectES.size() == 0) {
                continue;
            }
            item.setTitle(subjectES.first().text());

            Elements subjectAES = subjectES.first().select("a");
            if (subjectAES.size() == 0) {
                continue;
            }
            String href = subjectAES.first().attr("href");
            item.setTid(HttpUtils.getMiddleString(href, "tid=", "&"));

            Elements authorAES = tbodyE.select("tr td.author cite a");
            if (authorAES.size() == 0) {
                continue;
            }
            item.setAuthor(authorAES.first().text());

            String spaceUrl = authorAES.first().attr("href");
            if (!TextUtils.isEmpty(spaceUrl)) {
                String uid = HttpUtils.getMiddleString(spaceUrl, "uid=", "&");
                item.setAvatarUrl(HiUtils.getAvatarUrlByUid(uid));
            }

            Elements timeES = tbodyE.select("tr td.author em");
            if (timeES.size() > 0) {
                item.setTime(item.getAuthor() + "  " + timeES.first().text());
            }

            Elements forumES = tbodyE.select("tr td.forum");
            if (forumES.size() > 0) {
                item.setForum(forumES.first().text());
            }

            list.add(item);
        }

        return list;
    }

    private static SimpleListBean parseSearchFullText(Document doc) {
        if (doc == null) {
            return null;
        }

        SimpleListBean list = new SimpleListBean();
        int last_page = 1;

        //if this is the last page, page number is in <strong>
        Elements pagesES = doc.select("div.pages_btns div.pages a");
        pagesES.addAll(doc.select("div.pages_btns div.pages strong"));
        String searchIdUrl;
        if (pagesES.size() > 0) {
            searchIdUrl = pagesES.first().attr("href");
            list.setSearchIdUrl(searchIdUrl);
            for (Node n : pagesES) {
                int tmp = HttpUtils.getIntFromString(((Element) n).text());
                if (tmp > last_page) {
                    last_page = tmp;
                }
            }
        }
        list.setMaxPage(last_page);

        Elements tbodyES = doc.select("table.datatable tr");
        for (int i = 0; i < tbodyES.size(); ++i) {
            Element trowE = tbodyES.get(i);
            SimpleListItemBean item = new SimpleListItemBean();

            Elements subjectES = trowE.select("div.sp_title a");
            if (subjectES.size() == 0) {
                continue;
            }
            item.setTitle(subjectES.first().text());
            //gotopost.php?pid=12345
            String postUrl = Utils.nullToText(subjectES.first().attr("href"));
            item.setPid(HttpUtils.getMiddleString(postUrl, "pid=", "&"));
            if (TextUtils.isEmpty(item.getPid())) {
                continue;
            }

            Elements contentES = trowE.select("div.sp_content");
            if (contentES.size() > 0) {
                item.setInfo(contentES.text());
            }

//            <div class="sp_theard">
//            <span class="sp_w200">版块: <a href="forumdisplay.php?fid=2">Discovery</a></span>
//            <span>作者: <a href="space.php?uid=189027">tsonglin</a></span>
//            <span>查看: 1988</span>
//            <span>回复: 56</span>
//            <span class="sp_w200">最后发表: 2015-4-4 21:58</span>
//            </div>
            Elements postInfoES = trowE.select("div.sp_theard span");
            if (postInfoES.size() != 5) {
                continue;
            }
            Elements authorES = postInfoES.get(1).select("a");
            if (authorES.size() > 0) {
                item.setAuthor(authorES.first().text());
                String spaceUrl = authorES.first().attr("href");
                if (!TextUtils.isEmpty(spaceUrl)) {
                    String uid = HttpUtils.getMiddleString(spaceUrl, "uid=", "&");
                    item.setAvatarUrl(HiUtils.getAvatarUrlByUid(uid));
                }
            }

            item.setTime(item.getAuthor() + " " + HttpUtils.getMiddleString(postInfoES.get(4).text(), ":", "&"));

            Elements forumES = postInfoES.get(0).select("a");
            if (forumES.size() > 0)
                item.setForum(forumES.first().text());

            list.add(item);
        }

        return list;
    }

    private static SimpleListBean parseFavorites(Document doc) {
        if (doc == null) {
            return null;
        }

        SimpleListBean list = new SimpleListBean();

        int last_page = 1;
        //if this is the last page, page number is in <strong>
        Elements pagesES = doc.select("div.pages a");
        pagesES.addAll(doc.select("div.pages strong"));
        if (pagesES.size() > 0) {
            for (Node n : pagesES) {
                int tmp = HttpUtils.getIntFromString(((Element) n).text());
                if (tmp > last_page) {
                    last_page = tmp;
                }
            }
        }
        list.setMaxPage(last_page);

        Elements trES = doc.select("table.datatable tbody tr");
        for (int i = 0; i < trES.size(); ++i) {
            Element trE = trES.get(i);
            SimpleListItemBean item = new SimpleListItemBean();

            Elements subjectES = trE.select("th");
            if (subjectES.size() == 0) {
                continue;
            }
            item.setTitle(subjectES.first().text());

            Elements subjectAES = subjectES.first().select("a");
            if (subjectAES.size() == 0) {
                continue;
            }
            String href = subjectAES.first().attr("href");
            item.setTid(HttpUtils.getMiddleString(href, "tid=", "&"));

            Elements timeES = trE.select("td.lastpost");
            if (timeES.size() > 0) {
                item.setTime(timeES.first().text().trim());
            }

            Elements forumES = trE.select("td.forum");
            if (forumES.size() > 0) {
                item.setForum(forumES.first().text().trim());
            }

            list.add(item);
        }

        return list;
    }

    public static UserInfoBean parseUserInfo(String rsp) {
        Document doc = Jsoup.parse(rsp);
        if (doc == null) {
            return null;
        }

        UserInfoBean info = new UserInfoBean();

        Elements usernameES = doc.select("div#profilecontent div.itemtitle h1");
        if (usernameES.size() > 0) {
            info.setUsername(Utils.nullToText(usernameES.first().text()).trim());
        }

        Elements onlineImgES = doc.select("div#profilecontent div.itemtitle img");
        if (onlineImgES.size() > 0) {
            info.setOnline(Utils.nullToText(onlineImgES.first().attr("src")).contains("online"));
        }

        Elements uidES = doc.select("div#profilecontent div.itemtitle ul li");
        if (uidES.size() > 0) {
            info.setUid(HttpUtils.getMiddleString(uidES.first().text(), "(UID:", ")").trim());
        }

        Elements avatarES = doc.select("div.side div.profile_side div.avatar img");
        if (avatarES.size() != 0) {
            info.setAvatarUrl(avatarES.first().attr("src"));
        }

        StringBuilder sb = new StringBuilder();

        Elements titleES = doc.select("h3.blocktitle");
        int i = 0;
        for (Element titleEl : titleES) {
            sb.append(titleEl.text()).append("\n\n");
            if (i == 0) {
                Elements detailES = doc.select("div.main div.s_clear ul.commonlist li");
                for (Element detail : detailES) {
                    sb.append(detail.text()).append('\n');
                }
            }
            i++;
            sb.append("\n");
            if (i >= 2)
                break;
        }


        info.setDetail(sb.toString());

        return info;
    }
}
