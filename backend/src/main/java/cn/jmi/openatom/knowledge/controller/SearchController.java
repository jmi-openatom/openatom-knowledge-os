package cn.jmi.openatom.knowledge.controller;

import cn.jmi.openatom.knowledge.dto.RagDtos;
import cn.jmi.openatom.knowledge.service.SearchIndexService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {
  private final SearchIndexService service;

  @GetMapping
  public List<RagDtos.SearchResult> search(@RequestParam("q") String query) {
    return service.search(query);
  }
}
