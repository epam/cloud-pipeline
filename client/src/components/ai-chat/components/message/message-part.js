import React, {Component} from 'react';
import PropTypes from 'prop-types';
import Markdown from '../../../special/markdown';

class MessagePart extends Component {
  state = {
    displayedText: '',
    fullText: '',
    index: 0,
    alive: false
  };

  rafId = null;

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps) {
    if (
      prevProps.text !== this.props.text ||
      prevProps.alive !== this.props.alive
    ) {
      this.updateFromProps();
    }
  }

  componentWillUnmount () {
    this.stopTyping();
  }

  updateFromProps = () => {
    const {alive = false, text = ''} = this.props;
    const {alive: prevAlive, fullText: prevText} = this.state;

    this.stopTyping();

    const live = alive || prevAlive;

    if (prevText.length > 0 && text.indexOf(prevText) === 0) {
      this.setState(
        {
          displayedText: live ? this.state.displayedText : '',
          fullText: text,
          index: live ? this.state.index : 0,
          alive: live
        },
        this.startTyping
      );
    } else if (alive) {
      this.setState(
        {
          displayedText: '',
          fullText: text,
          index: 0,
          alive
        },
        this.startTyping
      );
    } else {
      this.setState(
        {
          displayedText: text,
          fullText: text,
          index: text.length,
          alive
        },
        this.startTyping
      );
    }
  };

  startTyping = () => {
    if (!this.state.alive) return;

    this.stopTyping();
    let startTime = null;

    const velocity = this.props.velocity || 500; // chars per second

    const tick = (timestamp) => {
      if (!startTime) {
        startTime = timestamp;
      }

      const elapsedMs = timestamp - startTime;
      startTime = timestamp;
      const elapsedSeconds = elapsedMs / 1000;

      const addChars = Math.floor(elapsedSeconds * velocity);
      const nextIndex = Math.min(
        this.state.displayedText.length + addChars,
        this.state.fullText.length
      );

      if (nextIndex !== this.state.index) {
        this.setState({
          index: nextIndex,
          displayedText: this.state.fullText.slice(0, nextIndex)
        });
      }

      if (nextIndex < this.state.fullText.length) {
        this.rafId = requestAnimationFrame(tick);
      }
    };

    this.rafId = requestAnimationFrame(tick);
  };

  stopTyping = () => {
    if (this.rafId) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
  };

  render () {
    const {
      className,
      style
    } = this.props;

    const {displayedText} = this.state;

    return (
      <Markdown
        className={className}
        style={style}
        target="_blank"
        md={displayedText}
      />
    );
  }
}

MessagePart.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  text: PropTypes.string,
  velocity: PropTypes.number, // chars per second
  alive: PropTypes.bool
};

export default MessagePart;
