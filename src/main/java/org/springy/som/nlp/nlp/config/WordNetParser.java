package org.springy.som.nlp.nlp.config;

import jakarta.annotation.PostConstruct;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.PointerUtils;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.list.PointerTargetNodeList;
import net.sf.extjwnl.dictionary.Dictionary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WordNetParser {
    private Dictionary dictionary;

    @PostConstruct
    public void init() throws JWNLException {
        dictionary = Dictionary.getDefaultResourceInstance();
    }

    public List<Synset> getSynsets(POS pos, String word) throws JWNLException {
        IndexWord indexWord = dictionary.lookupIndexWord(pos, word);
        if (indexWord != null) {
            return indexWord.getSenses();
        } else {
            return null;
        }
    }

    public String getDefinition(Synset synset) {
        return synset.getGloss();
    }

    public PointerTargetNodeList getHypernyms(Synset synset) throws JWNLException, CloneNotSupportedException {
        return PointerUtils.getDirectHypernyms(synset).deepClone();
    }
}

