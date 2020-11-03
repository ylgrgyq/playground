"use strict";
function add(a, b) {
    return a + b;
}
var x = undefined;
var y = null;
console.log(x === y);
if (x) {
    console.log('haha');
}
if (y) {
    console.log('hohoho');
}
add(10, 20); // evaluates to 30
add.apply(null, [10, 20]); // evaluates to 30
add.call(null, 10, 20); // evaluates to 30
add.bind(null, 10, 20)(); // evaluates to 30
add.bind(null, 10)(20); // evaluates to 30
