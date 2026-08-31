import * as React from "react";
import {i18n} from "@quinscape/qlive-ts";

const TestComponent = ({}) => {

    return (
        <div className="qlive-placeholder">
            <h1> { i18n("Test") }</h1>
            <p>
                { i18n("Welcome {0}", "anonymous") }
            </p>
        </div>
    );
};

export default TestComponent;
