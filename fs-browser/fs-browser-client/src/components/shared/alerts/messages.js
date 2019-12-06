import {observable} from 'mobx';

class Messages {
  @observable items = [];

  identifier = 0;

  loading = (title) => {
    const id = this.identifier + 1;
    const message = {
      id,
      title,
      type: 'loading',
    };
    this.identifier += 1;
    const remove = () => {
      const [item] = this.items.filter(i => i.id === id);
      if (item) {
        const index = this.items.indexOf(item);
        this.items.splice(index, 1);
      }
    };
    this.items.push(message);
    return remove;
  };

  error = (title, duration = 5) => {
    const id = this.identifier + 1;
    const message = {
      id,
      title,
      type: 'warning',
    };
    this.identifier += 1;
    const remove = () => {
      const [item] = this.items.filter(i => i.id === id);
      if (item) {
        const index = this.items.indexOf(item);
        this.items.splice(index, 1);
      }
    };
    this.items.push(message);
    setTimeout(remove, duration * 1000);
    return remove;
  };
}

export default new Messages();
