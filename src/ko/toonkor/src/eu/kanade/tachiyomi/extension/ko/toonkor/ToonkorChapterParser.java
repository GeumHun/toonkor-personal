package eu.kanade.tachiyomi.extension.ko.toonkor;

import eu.kanade.tachiyomi.source.model.SChapter;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

final class ToonkorChapterParser {
  private static final String CHAPTER_SELECTOR = "table.web_list tr:has(td.content__title)";
  private static final String TITLE_SELECTOR = "td.content__title";
  private static final String DATE_SELECTOR = "td.episode__index";

  private ToonkorChapterParser() {}

  static List<SChapter> parse(Document document, SimpleDateFormat dateFormat) {
    Elements elements = document.select(CHAPTER_SELECTOR);
    ArrayList<SChapter> chapters = new ArrayList<>(elements.size());

    for (int index = 0; index < elements.size(); index++) {
      Element element = elements.get(index);
      Elements titleElement = element.select(TITLE_SELECTOR);
      SChapter chapter = SChapter.Companion.create();

      chapter.setUrl(titleElement.attr("data-role"));
      chapter.setName(titleElement.text());
      chapter.setDate_upload(parseDate(dateFormat, element.select(DATE_SELECTOR).text()));
      chapters.add(chapter);
    }

    return chapters;
  }

  private static long parseDate(SimpleDateFormat dateFormat, String value) {
    Date parsedDate = dateFormat.parse(value, new ParsePosition(0));
    return parsedDate == null ? 0L : parsedDate.getTime();
  }
}
