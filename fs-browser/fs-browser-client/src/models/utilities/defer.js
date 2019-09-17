const defer = () => new Promise(resolve => process.nextTick(resolve));
export default defer;
