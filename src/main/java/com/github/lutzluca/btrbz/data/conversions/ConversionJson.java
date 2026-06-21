package com.github.lutzluca.btrbz.data.conversions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

final class ConversionJson {

    static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(ProductNameSource.class, new ProductNameSourceAdapter())
        .setPrettyPrinting()
        .create();

    private ConversionJson() { }
}
