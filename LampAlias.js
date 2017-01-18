var action = {};
var states = {};


function run(input) {
    var object = JSON.parse(input)
    action = object.action;
    states = object.states;
    if (action.hasOwnProperty('setPower')) {
        interpretSetPower();
    } else if (action.hasOwnProperty('setBrightness')) {
        interpretSetBrightness();
    }
    str = JSON.stringify(states);
    print(str);
}


function interpretSetPower() {
    if (action.setPower == true) {
        states.power = true;
        if (states.brightness < 10) {
            states.brightness = 10;
        }
    } else {
        states.power = false;
    }
}

function interpretSetBrightness() {

    if (action.setBrightness != states.brightness) {
        states.power = true;
    }

    if (action.setBrightness == 0) {
        states.power = false;
    }

    states.brightness = action.setBrightness;
}

