import { EventEmitter } from "events";


class SomeSet {
    add(value: number): SomeSet {
        return this;
    }
}

class SomeSet2 extends SomeSet {

}

let emitter: EventEmitter;

