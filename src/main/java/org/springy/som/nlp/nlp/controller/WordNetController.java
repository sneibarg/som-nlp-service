package org.springy.som.nlp.nlp.controller;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.list.PointerTargetNode;
import org.springy.som.nlp.nlp.config.WordNetParser;
import org.springy.som.nlp.nlp.dto.SynsetDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/wordnet")
public class WordNetController {

    @Autowired
    private WordNetParser wordNetParser;

    @GetMapping("/synsets")
    public Map<String, Object> getWordData(@RequestParam String word,
                                           @RequestParam String pos) throws JWNLException {
        Map<String, Object> response = new HashMap<>();
        List<Synset> synsets = wordNetParser.getSynsets(POS.getPOSForLabel(pos), word);
        if (synsets != null) {
            response.put("word", word);
            response.put("synsets", synsets.stream().map(synset -> {
                SynsetDTO synsetDTO = new SynsetDTO();
                synsetDTO.setId(synset.getKey().toString());
                synsetDTO.setDefinition(wordNetParser.getDefinition(synset));
                try {
                    synsetDTO.setHypernyms(wordNetParser.getHypernyms(synset).stream().map(PointerTargetNode::toString).collect(Collectors.toList()));
                } catch (JWNLException | CloneNotSupportedException e) {
                    synsetDTO.setHypernyms(null);
                }
                return synsetDTO;
            }).collect(Collectors.toList()));
        } else {
            response.put("error", "Word not found");
        }
        return response;
    }
}


