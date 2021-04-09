// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.mydomain.example;

import com.yahoo.application.Application;
import com.yahoo.application.Networking;
import com.yahoo.application.container.Processing;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import org.junit.Test;

import java.nio.file.FileSystems;

import static org.junit.Assert.assertEquals;

/**
 * Unit test of the container aspect of this application.
 */
public class ApplicationTest {

    @Test
    public void requireThatResultContainsHelloWorld() {
        try (Application app = Application.fromApplicationPackage(
                FileSystems.getDefault().getPath("src/main/application"),
                Networking.disable)) {
            Processing processing = app.getJDisc("jdisc").processing();
            Response response = processing.process(ComponentSpecification.fromString("default"), new Request());
            assertEquals("Hello, services!", response.data().get(0).toString());
        }
    }

}
