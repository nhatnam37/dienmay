package com.dienmay.inventory.dto;

import java.util.*;

public class StocktakeCountForm {
    private List<StocktakeCountLineForm> lines = new ArrayList<>();
    public List<StocktakeCountLineForm> getLines() { return lines; }
    public void setLines(List<StocktakeCountLineForm> lines) { this.lines = lines; }
}
